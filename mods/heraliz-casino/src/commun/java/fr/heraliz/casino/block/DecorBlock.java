package fr.heraliz.casino.block;

import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.casino.entity.SeatEntity;
import fr.heraliz.casino.machine.Footprint;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Element de decor du casino. Rendu entierement par son modele 3D ; les pieces murales se posent contre la face du
 * bloc visee, les autres face au joueur.
 * <ul>
 * <li>Sieges (fauteuil, matelas, botte de paille, canapes...) : clic droit de n'importe quel joueur = s'asseoir
 * (accroupi pour se relever) ; le personnel, accroupi, change la variante.</li>
 * <li>Porte du coffre-fort : clic droit de n'importe quel joueur = ouvrir / fermer.</li>
 * <li>Poteaux a chaine et potelets : le personnel decroche ou raccroche la chaine du cote clique ; accroupi, toutes
 * les chaines du poteau.</li>
 * <li>Le reste : le personnel (creatif ou operateur) change la variante au clic droit (texte, motif, couleur,
 * chaine...) et allume ou eteint l'element accroupi. Un element sans variante ni lumiere ne fait rien.</li>
 * </ul>
 */
public class DecorBlock extends BaseEntityBlock implements FootprintBlock {
    /** Allume (lumiere, flammes, ecran, chaine accrochee) ; porte du coffre : ouverte. */
    public static final BooleanProperty ON = BlockStateProperties.LIT;
    /** Portee de la chaine d'un poteau vers son voisin (blocs). */
    public static final int CHAIN_REACH = 6;
    /** Les quatre cotes d'un poteau decroches. */
    public static final int ALL_SIDES = 0b1111;

    public final DecorType type;

    public DecorBlock(DecorType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(MachineBlock.FACING, Direction.SOUTH).setValue(ON, true));
    }

    @Override
    public Footprint footprint() {
        return type.footprint;
    }

    @Override
    public Footprint footprint(BlockState state) {
        return type.footprint(state.hasProperty(ON) && state.getValue(ON));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachineBlock.FACING, ON);
    }

    // ------------------------------------------------------------------ placement
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = type.wall() && face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (int[] c : type.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(ctx)) {
                if (!level.isClientSide && ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.translatable("message.heralizcasino.no_space").withStyle(ChatFormatting.RED), true);
                }
                return null;
            }
        }
        // une porte se pose fermee ; tout le reste, allume
        return defaultBlockState().setValue(MachineBlock.FACING, facing).setValue(ON, !type.door());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level.isClientSide) return;
        boolean fresh = !oldState.is(state.getBlock());
        if (fresh) placeParts(level, pos, state);
        // lumiere qui gresille : le serveur la fait varier tant qu'elle est allumee (relancee a la pose et a l'allumage,
        // pas a chaque saute de lumiere : le tick suivant est deja programme)
        if (type.flicker() && state.getValue(ON) && (fresh || !oldState.getValue(ON))) level.scheduleTick(pos, this, 2);
    }

    private void placeParts(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(MachineBlock.FACING);
        for (int[] c : type.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (!level.isInWorldBounds(p)) continue;
            BlockState cur = level.getBlockState(p);
            BlockState part = MachinePartBlock.forOffset(w[0], w[1], w[2]);
            if (part == null || cur == part) continue;
            if (cur.canBeReplaced()) level.setBlock(p, part, 3);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            Direction facing = state.getValue(MachineBlock.FACING);
            for (int[] c : type.footprint.cells) {
                if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
                int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
                BlockPos p = pos.offset(w[0], w[1], w[2]);
                BlockState cur = level.getBlockState(p);
                if (cur.getBlock() instanceof MachinePartBlock && pos.equals(MachinePartBlock.masterPos(p, cur))) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ clic droit
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!type.interactive()) return InteractionResult.PASS;
        boolean staff = player.isCreative() || player.hasPermissions(2);
        boolean sneak = player.isShiftKeyDown();
        // ---- porte du coffre-fort : tout le monde l'ouvre et la ferme
        if (type.door()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            boolean open = !state.getValue(ON);
            level.setBlock(pos, state.setValue(ON, open), 3);
            level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.55f);
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            return InteractionResult.CONSUME;
        }
        // ---- sieges : tout le monde s'assoit ; le personnel, accroupi, change la variante
        if (type.seated() && !(sneak && staff && type.modes > 1)) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DecorBlockEntity be) sit((ServerLevel) level, pos, state, sp, be, hit.getLocation());
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!staff) {
            player.displayClientMessage(Component.translatable("message.heralizcasino.decor.staff").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof DecorBlockEntity be)) return InteractionResult.CONSUME;
        Component msg;
        if (type.linked() && !sneak) {
            // poteaux : la chaine du cote clique
            msg = toggleSide(level, pos, state, be, chainSide(pos, player, hit));
        } else if (type.switchable() && (sneak || type.modes == 1)) {
            boolean on = !state.getValue(ON);
            level.setBlock(pos, state.setValue(ON, on), 3);
            // poteau raccroche : toutes ses chaines reviennent
            if (on && type.linked()) hookAll(level, pos, be);
            String key = type.linked() ? (on ? "chain_on" : "chain_off") : (on ? "on" : "off");
            msg = Component.translatable("message.heralizcasino.decor." + key);
        } else if (type.modes > 1) {
            // les affiches et les pancartes se feuillettent dans les deux sens (accroupi : modele precedent)
            int m = Math.floorMod(be.mode + (sneak && !type.switchable() && !type.seated() ? -1 : 1), type.modes);
            be.setMode(m);
            msg = type.modeName(m);
            if (!state.getValue(ON) && type.switchable()) level.setBlock(pos, state.setValue(ON, true), 3);
            if (type.flicker()) level.scheduleTick(pos, this, 2);
        } else {
            return InteractionResult.PASS;
        }
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.YELLOW), true);
        if (type.linked()) level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.1f);
        else level.playSound(null, pos, type == DecorType.AFFICHE || type == DecorType.PANCARTE ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS, 0.4f, 1.4f);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ chaines des poteaux
    public static int sideBit(Direction d) {
        return 1 << d.get2DDataValue();
    }

    /**
     * Poteau du meme type que la chaine de ce cote rejoindrait : le premier trouve a portee, sans bloc plein entre
     * les deux (null s'il n'y en a pas, ou s'il est colle : deux poteaux voisins ne se relient pas).
     */
    @Nullable
    public static BlockPos chainPartner(BlockGetter level, BlockPos pos, DecorType type, Direction side) {
        for (int i = 1; i <= CHAIN_REACH; i++) {
            BlockPos q = pos.relative(side, i);
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof DecorBlock b && b.type == type) return i > 1 ? q : null;
            if (s.isCollisionShapeFullBlock(level, q)) return null;
        }
        return null;
    }

    /** Cotes decroches du poteau a cette position (0 si ce n'en est pas un). */
    public static int unhooked(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DecorBlockEntity be ? be.unhooked : 0;
    }

    /** Cote vise : la face cliquee ; sur le dessus, le bord le plus proche, ou le cote tourne vers le joueur. */
    private static Direction chainSide(BlockPos pos, Player player, BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (face.getAxis().isHorizontal()) return face;
        double dx = hit.getLocation().x - (pos.getX() + 0.5), dz = hit.getLocation().z - (pos.getZ() + 0.5);
        if (Math.max(Math.abs(dx), Math.abs(dz)) < 0.06) return player.getDirection().getOpposite();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /**
     * Decroche ou raccroche la chaine d'un cote. Les deux bouts sont mis d'accord (le poteau d'en face aussi) ; un
     * poteau entierement decroche ne raccroche que ce cote-la.
     */
    private Component toggleSide(Level level, BlockPos pos, BlockState state, DecorBlockEntity be, Direction side) {
        int bit = sideBit(side), back = sideBit(side.getOpposite());
        BlockPos q = chainPartner(level, pos, type, side);
        DecorBlockEntity other = q != null && level.getBlockEntity(q) instanceof DecorBlockEntity o ? o : null;
        boolean hook;
        if (!state.getValue(ON)) {
            level.setBlock(pos, state.setValue(ON, true), 3);
            be.setUnhooked(ALL_SIDES & ~bit);
            hook = true;
        } else {
            hook = (be.unhooked & bit) != 0 || other != null && (other.unhooked & back) != 0;
            be.setUnhooked(hook ? be.unhooked & ~bit : be.unhooked | bit);
        }
        if (other != null) {
            BlockState os = level.getBlockState(q);
            if (hook && !os.getValue(ON)) {
                level.setBlock(q, os.setValue(ON, true), 3);
                other.setUnhooked(ALL_SIDES & ~back);
            } else {
                other.setUnhooked(hook ? other.unhooked & ~back : other.unhooked | back);
            }
        }
        Component where = Component.translatable("message.heralizcasino.side." + side.getName());
        return Component.translatable("message.heralizcasino.decor." + (hook ? "chain_side_on" : "chain_side_off"), where);
    }

    /** Toutes les chaines du poteau raccrochees, des deux bouts. */
    private void hookAll(Level level, BlockPos pos, DecorBlockEntity be) {
        be.setUnhooked(0);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = chainPartner(level, pos, type, d);
            if (q != null && level.getBlockEntity(q) instanceof DecorBlockEntity o) o.setUnhooked(o.unhooked & ~sideBit(d.getOpposite()));
        }
    }

    /**
     * Installe le joueur a la place libre la plus proche de l'endroit clique (canape et matelas : deux places).
     * Il se releve en s'accroupissant, devant le siege.
     */
    private void sit(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer p, DecorBlockEntity be, Vec3 hit) {
        if (p.isSleeping()) return;
        DecorType.Seat[] seats = type.seats(be.mode).clone();
        Direction facing = state.getValue(MachineBlock.FACING);
        float[] h = Footprint.toModel(pos, facing, hit);
        java.util.Arrays.sort(seats, java.util.Comparator.comparingDouble(s -> (s.x() - h[0]) * (s.x() - h[0]) + (s.z() - h[2]) * (s.z() - h[2])));
        for (DecorType.Seat s : seats) {
            Vec3 at = Footprint.toWorld(pos, facing, s.x(), 0, s.z());
            if (p.getVehicle() instanceof SeatEntity cur && pos.equals(cur.machine()) && cur.position().distanceToSqr(at) < 0.01) return;
            AABB around = new AABB(at, at).inflate(0.2, 0.5, 0.2);
            if (!level.getEntitiesOfClass(SeatEntity.class, around, SeatEntity::isVehicle).isEmpty()) continue;
            Vec3 exit = Footprint.toWorld(pos, facing, s.exitX(), 0, s.exitZ());
            float yaw = Footprint.toWorldYaw(facing, s.lookX(), s.lookZ());
            if (p.isPassenger()) p.stopRiding();
            SeatEntity seat = SeatEntity.create(level, pos, at, exit, yaw, s.range(), s.feet(), true);
            if (!p.startRiding(seat, true)) {
                seat.discard();
                return;
            }
            p.setYRot(yaw);
            p.setYHeadRot(yaw);
            // la camera du joueur pivote vers l'avant du siege (il cliquait en lui faisant face)
            Vec3 ahead = Footprint.toWorld(pos, facing, s.x() + s.lookX() * 64, 0, s.z() + s.lookZ() * 64);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(ahead.x, p.getEyeY() - 0.3, ahead.z));
            level.playSound(null, pos, type == DecorType.BOTTE || type == DecorType.CABANE ? SoundEvents.GRASS_STEP : SoundEvents.WOOL_STEP,
                    SoundSource.BLOCKS, 0.6f, 0.9f);
            return;
        }
        p.displayClientMessage(Component.translatable("message.heralizcasino.decor.seat_taken").withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ effets (client)
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide ? createTickerHelper(beType, ModRegistry.DECOR_BE.get(), DecorBlockEntity::clientTick) : null;
    }

    // ------------------------------------------------------------------ forme & rendu
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return footprint(state).outline(state.getValue(MachineBlock.FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return footprint(state).collision(state.getValue(MachineBlock.FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType t) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(MachineBlock.FACING, rotation.rotate(state.getValue(MachineBlock.FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(MachineBlock.FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecorBlockEntity(pos, state);
    }
}
