package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rigole creusée dans le sol où coule le liquide radioactif. Les rigoles se raccordent d'elles-mêmes (rebords seulement du
 * côté sans voisine) ; la surface coule dans le sens FLOW (vers le centre de la salle), ou tourbillonne (bassins, jonctions).
 * COVERED : une grille par-dessus (passerelle). Y marcher irradie.
 */
public class RadChannelBlock extends Block {
   public enum Flow implements StringRepresentable {
      NONE, NORTH, EAST, SOUTH, WEST;

      @Override
      public String getSerializedName() {
         return name().toLowerCase(java.util.Locale.ROOT);
      }

      Flow rotate(Rotation r) {
         if (this == NONE) return this;
         Direction d = Direction.valueOf(name());
         return valueOf(r.rotate(d).name());
      }
   }

   public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
      WEST = BlockStateProperties.WEST;
   public static final EnumProperty<Flow> FLOW = EnumProperty.create("flow", Flow.class);
   public static final BooleanProperty COVERED = BooleanProperty.create("covered");
   /** A sec (salle du mode Zombies avant l'intro) : fond croute, ni liquide, ni particules, ni irradiation. La lumiere ne change
    * pas (aucune mise a jour d'eclairage quand les rigoles se remplissent). */
   public static final BooleanProperty DRY = BooleanProperty.create("dry");
   /** Hauteur de la surface du liquide (pixels). */
   public static final int LEVEL = 12;
   private static final VoxelShape[] SHAPES = new VoxelShape[16];

   static {
      for (int m = 0; m < 16; m++) {
         VoxelShape s = box(0, 0, 0, 16, LEVEL, 16);
         if ((m & 1) == 0) s = Shapes.or(s, box(0, LEVEL, 0, 16, 16, 2));
         if ((m & 2) == 0) s = Shapes.or(s, box(14, LEVEL, 0, 16, 16, 16));
         if ((m & 4) == 0) s = Shapes.or(s, box(0, LEVEL, 14, 16, 16, 16));
         if ((m & 8) == 0) s = Shapes.or(s, box(0, LEVEL, 0, 2, 16, 16));
         SHAPES[m] = s;
      }
   }

   /** Arguments du constructeur, pour l'usine DungeonRegistry.CHANNEL_USINE (version serveur : RadChannelBlockServeur). */
   public record Params(Properties props, boolean sludge, boolean lava) {}

   /** Boue toxique : empoisonne (sinon : liquide radioactif qui irradie). */
   public final boolean sludge;
   /** Rivière de lave (forge engloutie) : brûle. */
   public final boolean lava;

   public RadChannelBlock(Properties p, boolean sludge) {
      this(p, sludge, false);
   }

   /** Pour l'usine (DungeonRegistry.CHANNEL_USINE). */
   public RadChannelBlock(Params a) {
      this(a.props(), a.sludge(), a.lava());
   }

   public RadChannelBlock(Properties p, boolean sludge, boolean lava) {
      super(p);
      this.sludge = sludge;
      this.lava = lava;
      this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
         .setValue(FLOW, Flow.NONE).setValue(COVERED, false).setValue(DRY, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(NORTH, EAST, SOUTH, WEST, FLOW, COVERED, DRY);
   }

   private static int mask(BlockState s) {
      return (s.getValue(NORTH) ? 1 : 0) | (s.getValue(EAST) ? 2 : 0) | (s.getValue(SOUTH) ? 4 : 0) | (s.getValue(WEST) ? 8 : 0);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return s.getValue(COVERED) ? Shapes.block() : SHAPES[mask(s)];
   }

   private static BooleanProperty prop(Direction d) {
      return switch (d) {
         case NORTH -> NORTH;
         case EAST -> EAST;
         case SOUTH -> SOUTH;
         default -> WEST;
      };
   }

   private boolean joins(BlockState other) {
      return other.getBlock() instanceof RadChannelBlock;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      BlockState s = this.defaultBlockState();
      for (Direction d : Direction.Plane.HORIZONTAL) s = s.setValue(prop(d), joins(ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d))));
      return s;
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      if (dir.getAxis().isHorizontal()) return s.setValue(prop(dir), joins(other));
      return s;
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState rotate(BlockState s, Rotation r) {
      BlockState out = s;
      for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(prop(r.rotate(d)), s.getValue(prop(d)));
      return out.setValue(FLOW, s.getValue(FLOW).rotate(r));
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState mirror(BlockState s, Mirror m) {
      BlockState out = s;
      for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(prop(m.mirror(d)), s.getValue(prop(d)));
      Flow f = s.getValue(FLOW);
      if (f != Flow.NONE) out = out.setValue(FLOW, Flow.valueOf(m.mirror(Direction.valueOf(f.name())).name()));
      return out;
   }

   /**
    * Des deux côtés : on s'enfonce (le client déplace lui-même le joueur). Brûlure, poison, irradiation : RadChannelBlockServeur
    * (usine DungeonRegistry.CHANNEL_USINE).
    */
   @Override
   @SuppressWarnings("deprecation")
   public void entityInside(BlockState s, Level level, BlockPos pos, Entity e) {
      if (s.getValue(COVERED) || s.getValue(DRY) || !(e instanceof LivingEntity le) || e.getY() >= pos.getY() + LEVEL / 16.0 + 0.05) return;
      if (lava) {
         le.makeStuckInBlock(s, new net.minecraft.world.phys.Vec3(0.6, 0.8, 0.6));
         return;
      }
      // la boue colle aux bottes (des deux côtés : le client déplace lui-même le joueur)
      if (sludge) le.makeStuckInBlock(s, new net.minecraft.world.phys.Vec3(0.75, 0.9, 0.75));
   }

   @Override
   public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
      if (s.getValue(DRY)) return;
      double y = pos.getY() + LEVEL / 16.0 + 0.02;
      if (lava) {
         if (r.nextInt(12) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, pos.getX() + 0.5, y, pos.getZ() + 0.5, 0, 0, 0);
         if (r.nextInt(8) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + r.nextDouble(), y + 0.1, pos.getZ() + r.nextDouble(), 0, 0.03, 0);
         }
         if (r.nextInt(200) == 0) {
            level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.LAVA_POP, net.minecraft.sounds.SoundSource.BLOCKS,
               0.5F, 0.9F + r.nextFloat() * 0.2F, false);
         }
         return;
      }
      if (r.nextInt(7) == 0) {
         level.addParticle(ModParticles.ACID_SPLASH.get(), pos.getX() + 0.2 + r.nextDouble() * 0.6, y, pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.03, 0);
      }
      if (r.nextInt(18) == 0) {
         level.addParticle(ModParticles.TOXIC_SMOKE.get(), pos.getX() + r.nextDouble(), y + 0.1, pos.getZ() + r.nextDouble(), 0, 0.012, 0);
      }
      if (!sludge && r.nextInt(40) == 0) {
         level.addParticle(ModParticles.RAD_EMBER.get(), pos.getX() + r.nextDouble(), y + 0.2, pos.getZ() + r.nextDouble(), 0, 0.02, 0);
      }
   }
}
