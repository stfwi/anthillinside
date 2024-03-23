/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import wile.anthillinside.ModContent;
import wile.anthillinside.libmc.Overlay;
import wile.anthillinside.libmc.StandardBlocks;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Inventories;


import java.util.*;
import java.util.stream.Stream;


public class RedAntTrail
{
  public static void on_config()
  {}

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntTrailBlock extends StandardBlocks.HorizontalWaterLoggable
  {
    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty IN = BooleanProperty.create("in");
    public static final BooleanProperty DROP = BooleanProperty.create("drop");

    public RedAntTrailBlock(long config, BlockBehaviour.Properties props)
    {
      super(config, props.isRedstoneConductor((s,w,p)->false), (states)->{
        final HashMap<BlockState, VoxelShape> shapes = new HashMap<>();
        final AABB base_aabb = Auxiliaries.getPixeledAABB(0,0,0,16,0.2,16);
        final AABB up_aabb   = Auxiliaries.getPixeledAABB(0,0,0,16,16,0.2);
        for(BlockState state:states) {
          final Direction facing = state.getValue(HORIZONTAL_FACING);
          VoxelShape shape = Shapes.empty();
          if(state.getValue(UP)) {
            shape = Shapes.joinUnoptimized(shape, Shapes.create(Auxiliaries.getRotatedAABB(up_aabb, facing, true)), BooleanOp.OR);
          }
          if(state.getValue(FRONT) || (!state.getValue(UP))) {
            shape = Shapes.joinUnoptimized(shape, Shapes.create(Auxiliaries.getRotatedAABB(base_aabb, facing, true)), BooleanOp.OR);
          }
          shapes.putIfAbsent(state, shape);
        }
        return shapes;
      });
      registerDefaultState(super.defaultBlockState().setValue(FRONT, true).setValue(UP, false).setValue(LEFT, false).setValue(RIGHT, false).setValue(IN, false).setValue(DROP, false));
    }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public Item asItem()
    { return ModContent.references.ANTS_ITEM; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
    { return !state.getValue(DROP) ? Collections.singletonList(new ItemStack(asItem())) : List.of(new ItemStack(asItem()), new ItemStack(Items.LADDER)); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(FRONT, LEFT, RIGHT, UP, IN, DROP); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return null;
      if((!state.getValue(UP)) && (context.getClickedFace().getAxis().isVertical()) && (!canSurvive(state, context.getLevel(), context.getClickedPos()))) return null;
      return updatedState(state.setValue(DROP, false), context.getLevel(), context.getClickedPos());
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      final Direction block_facing = state.getValue(HORIZONTAL_FACING);
      for(Direction facing: Direction.values()) {
        if(!facing.getAxis().isHorizontal()) continue;
        if(facing == block_facing) continue;
        final BlockPos diagonal_pos = pos.relative(facing).below();
        final BlockState diagonal_state = world.getBlockState(diagonal_pos);
        if(!diagonal_state.is(this)) continue;
        if(diagonal_state.getValue(UP)) continue;
        final Direction diagonal_facing = diagonal_state.getValue(HORIZONTAL_FACING);
        if(diagonal_facing != facing.getOpposite()) continue;
        world.setBlock(diagonal_pos, diagonal_state.setValue(UP, true), 2);
      }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos)
    {
      if(!(world instanceof Level)) return state;
      state = super.updateShape(state, facing, facingState, world, pos, facingPos);
      if(canSurvive(state, world, pos)) return updatedState(state, world, pos);
      return Blocks.AIR.defaultBlockState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos)
    {
      if(state.getValue(DROP)) return true;
      final BlockState state_below = world.getBlockState(pos.below());
      if(state_below.is(BlockTags.LEAVES) || state_below.isFaceSturdy(world, pos.below(), Direction.UP, SupportType.RIGID)) return true;
      final Direction facing = state.getValue(HORIZONTAL_FACING);
      return state.getValue(UP)
        ? Block.isFaceFull(world.getBlockState(pos.relative(facing)).getShape(world, pos.relative(facing)), facing.getOpposite())
        : Stream.of(pos.relative(facing), pos.relative(facing.getOpposite())).allMatch((np) ->
            world.getBlockState(np).getBlock() == this && Block.isFaceFull(world.getBlockState(np.below()).getShape(world, np.below()), Direction.UP)
          );
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr)
    {
      Direction facing = rtr.getDirection().getOpposite();
      ItemStack stack = player.getItemInHand(hand);
      if(facing == Direction.DOWN && !stack.isEmpty() && stack.is(Items.SCAFFOLDING)) {
        if(world.isClientSide()) return InteractionResult.SUCCESS;
        Overlay.TextOverlayGui.show(Auxiliaries.localizable("hints.use_ladder"), 1000);
        return InteractionResult.CONSUME;
      } else if(facing == Direction.DOWN && !stack.isEmpty() && stack.is(Items.LADDER)) {
        if(world.isClientSide()) {
          return InteractionResult.SUCCESS;
        } else {
          if(state.getValue(DROP)) {
            world.setBlock(pos, state.setValue(DROP, false), 10);
            world.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), SoundEvents.PAINTING_PLACE, SoundSource.PLAYERS, 0.2f, 1.0f);
            Inventories.give(player, new ItemStack(Items.LADDER, 1));
          } else {
            world.setBlock(pos, state.setValue(DROP, true), 10);
            world.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), SoundEvents.PAINTING_PLACE, SoundSource.PLAYERS, 0.2f, 1.0f);
            stack.shrink(1);
          }
          return InteractionResult.CONSUME;
        }
      } else {
        return world.getBlockState(pos.relative(facing)).use(world, player, hand, new BlockHitResult(
          rtr.getLocation(), rtr.getDirection(), pos.relative(facing), false)
        );
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity)
    {
      if(entity instanceof ItemEntity) {
        moveEntity(state, world, pos, entity);
        if(state.getValue(UP) && (!world.getBlockTicks().hasScheduledTick(pos, this))) {
          world.scheduleTick(pos, this, 60);
        }
      } else if(entity instanceof LivingEntity) {
        itchEntity(state, world, pos, entity);
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand)
    {
      if(!state.getValue(UP)) return;
      final BlockState st = world.getBlockState(pos.above());
      if(st.is(this)) return;
      final List<ItemEntity> entities = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos.above()).expandTowards(0,-0.2,0), Entity::isAlive);
      final Vec3 v = Vec3.atLowerCornerOf(state.getValue(HORIZONTAL_FACING).getNormal()).add(0,1,0).scale(0.1);
      for(ItemEntity entity:entities) entity.setDeltaMovement(v);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
    { world.setBlock(pos, updatedState(state, world, pos), 2); }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type)
    { return (!state.getValue(UP)) || super.isPathfindable(state, world, pos, type); }

    //------------------------------------------------------------------------------------------------------

    public BlockState updatedState(@Nullable BlockState state, LevelAccessor world, BlockPos pos)
    {
      if((state == null) || (!(world instanceof Level))) return state;
      final Direction facing = state.getValue(HORIZONTAL_FACING);
      final BlockState front_state = world.getBlockState(pos.relative(facing));
      final BlockState right_state = world.getBlockState(pos.relative(facing.getClockWise()));
      final BlockState left_state = world.getBlockState(pos.relative(facing.getCounterClockWise()));
      final boolean down_solid = Block.canSupportRigidBlock(world, pos.below());
      final boolean up_is_cube = world.getBlockState(pos.above()).isRedstoneConductor(world, pos.above());
      final boolean up = isFaceFull(front_state.getShape(world, pos.relative(facing)), facing.getOpposite())
        && ((!down_solid) || (world.getBlockState(pos.above()).is(this)) || ((!up_is_cube) && (world.getBlockState(pos.relative(facing).above()).is(this))));
      boolean left=false, right=false, front=down_solid, in=false;
      if(!up && (front_state.getBlock() != this)) {
        if(right_state.is(this) && right_state.getValue(HORIZONTAL_FACING) == facing.getClockWise()) right = true;
        if(left_state.is(this) && left_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise()) left = true;
        front = down_solid && !right && !left;
      } else if(world.hasNeighborSignal(pos)) {
        if(right_state.is(this) && right_state.getValue(HORIZONTAL_FACING) == facing.getClockWise()) right = true;
        if(left_state.is(this) && left_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise()) left = true;
        if(!right && !left) front = true;
      } else if (!right && !left && !up) {
        front = true;
      }
      if(down_solid) {
        if(right_state.is(this) && right_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise()) in = true;
        if(left_state.is(this) && left_state.getValue(HORIZONTAL_FACING) == facing.getClockWise()) in = true;
      }
      state = state.setValue(FRONT, front).setValue(RIGHT, right).setValue(LEFT, left).setValue(UP, up).setValue(IN, in);
      return state;
    }

    public void moveEntity(BlockState state, Level world, BlockPos pos, Entity any_entity)
    {
      if((!any_entity.isAlive()) || (!(any_entity instanceof final ItemEntity entity))) return;
      if(entity.getItem().isEmpty() || entity.getItem().getItem() == ModContent.references.ANTS_ITEM) return;
      final boolean up = state.getValue(UP);
      if(!up && !entity.onGround()) return;
      final Direction block_facing = state.getValue(HORIZONTAL_FACING);
      final boolean front = state.getValue(FRONT);
      final boolean right = state.getValue(RIGHT);
      final boolean left = state.getValue(LEFT);
      boolean outgoing = false, check_insertion_front = false, check_insertion_up = false;
      final Vec3 dp = entity.position().subtract(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).scale(2);
      final double progress = dp.get(block_facing.getAxis()) * Vec3.atLowerCornerOf(block_facing.getNormal()).get(block_facing.getAxis());
      Vec3 motion = Vec3.atLowerCornerOf(block_facing.getNormal()).scale(0.65);
      Direction insertion_facing;
      BlockState ahead_state;
      Direction dir;

      // Right diversion
      if(right) {
        insertion_facing = block_facing.getClockWise();
        ahead_state = world.getBlockState(pos.relative(insertion_facing));
        if(ahead_state.is(this)) {
          dir = ahead_state.getValue(HORIZONTAL_FACING);
          if(dir == insertion_facing) {
            motion = Vec3.atLowerCornerOf(insertion_facing.getNormal()).scale((progress < 0.4) ? 0.5 : 1.6);
            outgoing = true;
          }
        }
      }
      // Left diversion
      else if(left) {
        insertion_facing  = block_facing.getCounterClockWise();
        ahead_state = world.getBlockState(pos.relative(insertion_facing));
        if(ahead_state.is(this)) {
          dir = ahead_state.getValue(HORIZONTAL_FACING);
          if(dir == insertion_facing) {
            motion = Vec3.atLowerCornerOf(insertion_facing.getNormal()).scale((progress < 0.4) ? 0.5 : 1.6);
            outgoing = true;
          }
        }
      }
      // Itemframe sorting, only if not overridden using redstone.
      else if(front || up) {
        insertion_facing = this.itemFrameDiversion(world, pos, entity.getItem()).orElse(null);
        if (insertion_facing != null && insertion_facing != block_facing && insertion_facing != block_facing.getOpposite() && insertion_facing != Direction.UP) {
          if (insertion_facing == Direction.DOWN) {
            if (!world.isClientSide() && this.tryInsertItemEntity(world, pos, Direction.DOWN, entity)) {
              entity.setDeltaMovement(entity.getDeltaMovement().scale(0.7));
              return;
            }
          } else {
            motion = Vec3.atLowerCornerOf(insertion_facing.getNormal());
            outgoing = true;
          }
        }
      }
      // Motion update
      if(!outgoing && !front && !up) {
        motion = motion.scale(0);
      } else {
        if(!outgoing) {
          // Centering motion
          final Vec3 centering_motion = new Vec3(block_facing.getAxis() == Direction.Axis.X ? 0.0 : -0.2 * Math.signum(dp.x), 0.02, block_facing.getAxis() == Direction.Axis.Z ? 0.0 : -0.2 * Math.signum(dp.z));
          motion = motion.add(centering_motion);
          ahead_state = world.getBlockState(pos.relative(block_facing));
          if(ahead_state.is(this)) {
            dir = ahead_state.getValue(HORIZONTAL_FACING);
            if(dir == block_facing.getOpposite()) {
              motion = motion.scale(0.5);
            } else if(!ahead_state.getValue(RIGHT) && !ahead_state.getValue(LEFT)) {
              motion = motion.scale(2.0);
            } else {
              motion = motion.scale(1.6);
            }
          }
        }
        double y_speed = 0.1;
        if(!up) {
          y_speed = -0.1 * Math.min(dp.y, 0.5);
          if(progress > 0.5 && front) check_insertion_front = true;
        } else {
          if(front) motion = motion.scale(0.6);
          if(world.getBlockState(pos.above()).is(this)) {
            if(progress > 0.5) y_speed = 0.2;
            if(outgoing) { motion = new Vec3(motion.x(), 0.0, motion.z); y_speed = 0.08; }
          } else {
            if(front && Math.abs(dp.get(block_facing.getClockWise().getAxis())) > 0.4) {
              motion = new Vec3(motion.x, -0.1, motion.z);
              y_speed = 0.08;
            } else if(world.getBlockState(pos.above().relative(block_facing)).is(this)) {
              motion = motion.add(Vec3.atLowerCornerOf(block_facing.getNormal())).scale(0.5);
            }
            if(dp.y >= 0.4) check_insertion_up = true;
          }
        }
        if(progress > 0.8 && front) y_speed /= 2.0;
        if(motion.y < -0.1) motion = new Vec3(motion.x, -0.1, motion.z);
        motion = motion.scale(!up && dp.y > 0.0 ? 0.02 : 0.07).add(0.0, y_speed, 0.0);
      }
      // Container insertion
      if(!world.isClientSide()) {
        if(progress < 0.4 && state.getValue(DROP) && tryInsertItemEntity(world, pos, Direction.DOWN, entity)) {
          check_insertion_up = false;
          check_insertion_front = false;
        }
        if((check_insertion_front || check_insertion_up) && !entity.getItem().isEmpty()) {
          insertion_facing = check_insertion_up ? Direction.UP : block_facing;
          tryInsertItemEntity(world, pos, insertion_facing, entity);
        }
      }
      // Entity motion
      if(!up && state.getValue(WATERLOGGED)) motion = motion.add(0.0, -0.1, 0.0);
      motion = entity.getDeltaMovement().scale(0.5).add(motion);
      entity.setDeltaMovement(motion);
      entity.setOnGround(false);
    }

    public void itchEntity(BlockState state, Level world, BlockPos pos, Entity entity)
    {
      if((world.getRandom().nextDouble() > 4e-3) || (!entity.isAlive()) || (!entity.onGround())
         || (world.isClientSide()) || (entity.isShiftKeyDown()) || (!entity.canChangeDimensions()) || (entity.isInWaterOrRain()) || (entity.hasImpulse)
         || (!(entity instanceof LivingEntity))
      ) {
        return;
      }
      if(entity instanceof Monster) {
        entity.hurt(entity.damageSources().cactus(), 2f);
      } else if(entity instanceof Player) {
        if(world.getRandom().nextDouble() > 8e-2) return;
        entity.hurt(entity.damageSources().cactus(), 0.1f);
      } else {
        entity.hurt(entity.damageSources().cactus(), 0.0f);
        if(entity instanceof Villager) {
          ((Villager)entity).getBrain().setActiveActivityIfPossible(Activity.PANIC);
        } else if(entity instanceof Animal) {
          ((Animal)entity).getBrain().setActiveActivityIfPossible(Activity.PANIC);
        }
      }
    }

    private Optional<Direction> itemFrameDiversion(Level world, BlockPos pos, ItemStack match_stack)
    {
      List<ItemFrame> frames = world.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
      if(frames.isEmpty()) return Optional.empty();
      for(ItemFrame frame:frames) {
        if(!frame.getItem().is(match_stack.getItem())) continue;
        return Optional.of(frame.getDirection());
      }
      return Optional.empty();
    }

    private boolean tryInsertItemEntity(Level world, BlockPos pos, Direction insertion_facing, ItemEntity entity)
    {
      final ItemStack stack = entity.getItem();
      final int stack_size = stack.getCount();
      final ItemStack remaining = Inventories.insert(world, pos.relative(insertion_facing), insertion_facing.getOpposite(), stack, false);
      if(stack.isEmpty()) {
        entity.remove(Entity.RemovalReason.DISCARDED);
        return true;
      } else if(remaining.getCount() < stack_size) {
        entity.setItem(remaining);
        return true;
      } else {
        return false;
      }
    }
  }

}
