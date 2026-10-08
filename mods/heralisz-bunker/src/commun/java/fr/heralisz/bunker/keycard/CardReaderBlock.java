package fr.heralisz.bunker.keycard;

import com.hbm_m.block.entity.doors.DoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Lecteur de carte mural façon Rust. Carte de la bonne couleur -> lecture (le temps du glissement), accès accordé :
 * signal redstone pendant {@link KeycardConfig#PULSE_TICKS}, puis retour en attente. Mauvaise carte -> refus.
 * Le signal est émis dans toutes les directions et renforcé dans le bloc support (porte derrière le mur).
 * Incassable en survie : ce sont les admins qui les posent aux entrées des zones.
 */
public class CardReaderBlock extends HorizontalDirectionalBlock {
   public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
   public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
   /** Durée de la lecture = sortie de la carte + glissement (synchronisée avec la cinématique client). */
   public static final int READ_TICKS = 36;
   /** Délai entre « accès accordé » et l'ouverture de la porte (le temps que la caméra arrive sur la porte). */
   public static final int DOOR_DELAY = 10;
   private static final VoxelShape NORTH = Block.box(3, 1, 13, 13, 15, 16);
   private static final VoxelShape SOUTH = Block.box(3, 1, 0, 13, 15, 3);
   private static final VoxelShape WEST = Block.box(13, 1, 3, 16, 15, 13);
   private static final VoxelShape EAST = Block.box(0, 1, 3, 3, 15, 13);

   public final AccessLevel level;

   public CardReaderBlock(AccessLevel level, Properties props) {
      super(props);
      this.level = level;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.IDLE).setValue(POWERED, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(FACING, STATUS, POWERED);
   }

   @Nullable
   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      Direction face = ctx.getClickedFace();
      Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
      return this.defaultBlockState().setValue(FACING, facing);
   }

   @Override
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return switch (state.getValue(FACING)) {
         case SOUTH -> SOUTH;
         case WEST -> WEST;
         case EAST -> EAST;
         default -> NORTH;
      };
   }

   /** Appelé côté serveur quand un joueur glisse une carte. */
   public void swipe(Level level, BlockPos pos, BlockState state, Player player, ItemStack card, AccessLevel cardLevel) {
      if (state.getValue(STATUS) != Status.IDLE) {
         return;
      }
      boolean ok = cardLevel == this.level;
      level.setBlock(pos, state.setValue(STATUS, ok ? Status.READING_OK : Status.READING_KO), Block.UPDATE_ALL);
      level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9F, 1.6F);
      level.scheduleTick(pos, this, READ_TICKS);
      if (player instanceof ServerPlayer sp) {
         DoorBlockEntity door = ok ? KeycardDoors.doorsNear(level, pos, this.level).stream().findFirst().orElse(null) : null;
         KeycardNetwork.sendSwipe(sp, pos, state.getValue(FACING), ok, door);
      }
      if (ok && card.getItem() instanceof KeycardItem item) {
         item.consumeUse(card, player);
      }
   }

   @Override
   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      switch (state.getValue(STATUS)) {
         case READING_OK -> {
            // accès accordé : l'écran passe au vert, la porte s'ouvre quand la caméra est arrivée dessus
            level.setBlock(pos, state.setValue(STATUS, Status.GRANTED), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 1.8F);
            level.scheduleTick(pos, this, DOOR_DELAY);
         }
         case GRANTED -> {
            if (!state.getValue(POWERED)) {
               level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
               this.updateSupport(level, pos, state);
               KeycardDoors.notifyDoors(level, pos, this.level);
               // le signal dure au moins le temps d'ouverture de la porte (sinon elle resterait ouverte)
               int openTime = KeycardDoors.doorsNear(level, pos, this.level).stream().mapToInt(d -> d.getDoorDecl().getOpenTime()).max().orElse(0);
               level.scheduleTick(pos, this, Math.max(KeycardConfig.PULSE_TICKS.get(), openTime + 60));
            } else {
               level.setBlock(pos, state.setValue(STATUS, Status.IDLE).setValue(POWERED, false), Block.UPDATE_ALL);
               this.updateSupport(level, pos, state);
               KeycardDoors.notifyDoors(level, pos, this.level);
            }
         }
         case READING_KO -> {
            level.setBlock(pos, state.setValue(STATUS, Status.DENIED), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            level.scheduleTick(pos, this, 30);
         }
         case DENIED -> level.setBlock(pos, state.setValue(STATUS, Status.IDLE).setValue(POWERED, false), Block.UPDATE_ALL);
         default -> {
         }
      }
   }

   /** Prévient le bloc support et ses voisins (le signal traverse le mur). */
   private void updateSupport(Level level, BlockPos pos, BlockState state) {
      BlockPos support = pos.relative(state.getValue(FACING).getOpposite());
      level.updateNeighborsAt(pos, this);
      level.updateNeighborsAt(support, this);
   }

   @Override
   public boolean isSignalSource(BlockState state) {
      return true;
   }

   @Override
   public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(POWERED) ? 15 : 0;
   }

   @Override
   public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(POWERED) && dir == state.getValue(FACING) ? 15 : 0;
   }

   @Override
   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
      if (!moved && !state.is(newState.getBlock()) && state.getValue(POWERED)) {
         this.updateSupport(level, pos, state);
      }
      super.onRemove(state, level, pos, newState, moved);
   }

   public enum Status implements StringRepresentable {
      IDLE, READING_OK, READING_KO, GRANTED, DENIED;

      @Override
      public String getSerializedName() {
         return this.name().toLowerCase();
      }
   }
}
