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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraftforge.items.IItemHandler;
import wile.anthillinside.ModContent;
import wile.anthillinside.libmc.blocks.StandardBlocks;
import wile.anthillinside.libmc.detail.Auxiliaries;
import wile.anthillinside.libmc.detail.Inventories;

import javax.annotation.Nullable;
import java.util.*;


public class RedAntTrail
{
  public static void on_config()
  {}

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntTrailBlock extends StandardBlocks.HorizontalWaterLoggable implements StandardBlocks.IBlockItemFactory
  {
    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty IN = BooleanProperty.create("in");

    public RedAntTrailBlock(long config, BlockBehaviour.Properties builder)
    {
      super(config, builder, (states)->{
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
      registerDefaultState(super.defaultBlockState().setValue(FRONT, true).setValue(UP, false).setValue(LEFT, false).setValue(RIGHT, false).setValue(IN, false));
    }

    @Override
    public BlockItem getBlockItem(Block block, Item.Properties builder)
    { return ModContent.ANTS_ITEM; }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public Item asItem()
    { return ModContent.ANTS_ITEM; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(asItem())); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(FRONT, LEFT, RIGHT, UP, IN); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return null;
      if((!state.getValue(UP)) && (context.getClickedFace().getAxis().isVertical()) && (!Block.canSupportRigidBlock(context.getLevel(), context.getClickedPos().below()))) return null;
      return updatedState(state, context.getLevel(), context.getClickedPos());
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
      if(Block.isFaceFull(world.getBlockState(pos.below()).getShape(world, pos.below()), Direction.UP)) return true;
      if(!state.getValue(UP)) return false;
      Direction facing = state.getValue(HORIZONTAL_FACING);
      if(!Block.isFaceFull(world.getBlockState(pos.relative(facing)).getShape(world, pos.relative(facing)), facing.getOpposite())) return false;
      return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr)
    {
      Direction dir = rtr.getDirection().getOpposite();
      return world.getBlockState(pos.relative(dir)).use(world, player, hand, new BlockHitResult(
        rtr.getLocation(), rtr.getDirection(), pos.relative(dir), false
      ));
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
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rand)
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
    public boolean shouldCheckWeakPower(BlockState state, LevelReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side)
    { return true; }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type)
    { return (!state.getValue(UP)) || super.isPathfindable(state, world, pos, type); }

    //------------------------------------------------------------------------------------------------------

    public BlockState updatedState(@Nullable BlockState state, LevelAccessor world, BlockPos pos)
    {
      if((state == null) || (!(world instanceof Level))) return state;
      final Direction facing = state.getValue(HORIZONTAL_FACING);
      boolean down_solid = Block.canSupportRigidBlock(world, pos.below());
      boolean up_is_cube = world.getBlockState(pos.above()).isRedstoneConductor(world, pos.above());
      final boolean up = isFaceFull(world.getBlockState(pos.relative(facing)).getShape(world, pos.relative(facing)), facing.getOpposite())
        && ((!down_solid) || (world.getBlockState(pos.above()).is(this)) || ((!up_is_cube) && (world.getBlockState(pos.relative(facing).above()).is(this))));
      boolean left = false, right = false;
      boolean front = down_solid;
      boolean in = false;
      final BlockState right_state = world.getBlockState(pos.relative(facing.getClockWise()));
      final BlockState left_state = world.getBlockState(pos.relative(facing.getCounterClockWise()));
      if(((Level)world).hasNeighborSignal(pos)) {
        if(right_state.is(this) && (right_state.getValue(HORIZONTAL_FACING) == facing.getClockWise())) right = true;
        if(left_state.is(this) && (left_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise())) left = true;
        if(!right && !left) front = false;
      } else if(!right && !left && !up) {
        front = true;
      }
      if(down_solid) {
        if(right_state.is(this) && (right_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise())) in = true;
        if(left_state.is(this) && (left_state.getValue(HORIZONTAL_FACING) == facing.getClockWise())) in = true;
      }
      state = state.setValue(FRONT, front).setValue(RIGHT, right).setValue(LEFT, left).setValue(UP, up).setValue(IN, in);
      return state;
    }

    public void moveEntity(BlockState state, Level world, BlockPos pos, Entity any_entity)
    {
      if((!any_entity.isAlive()) || (!(any_entity instanceof final ItemEntity entity))) return;
      if(entity.getItem().isEmpty() || entity.getItem().getItem() == ModContent.ANTS_ITEM) return;
      final boolean up = state.getValue(UP);
      if(!up && !entity.isOnGround()) return;
      final Direction block_facing = state.getValue(HORIZONTAL_FACING);
      final boolean front = state.getValue(FRONT);
      boolean right = state.getValue(RIGHT);
      boolean left = state.getValue(LEFT);
      boolean outgoing = false, check_insertion_front = false, check_insertion_up = false;
      Vec3 motion = Vec3.atLowerCornerOf(block_facing.getNormal());
      final Vec3 dp = entity.position().subtract(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).scale(2);
      final double progress = dp.get(block_facing.getAxis()) * Vec3.atLowerCornerOf(block_facing.getNormal()).get(block_facing.getAxis());

      // Right diversion
      if(right) {
        final Direction facing_right = block_facing.getClockWise();
        final BlockState right_state = world.getBlockState(pos.relative(facing_right));
        if(right_state.is(this)) {
          final Direction dir = right_state.getValue(HORIZONTAL_FACING);
          if(dir == facing_right) {
            motion = Vec3.atLowerCornerOf(facing_right.getNormal()).scale((progress < 0.4) ? 0.7 : 1.5);
            outgoing = true;
          }
        }
      }
      // Left diversion
      else if(left) {
        final Direction facing_left  = block_facing.getCounterClockWise();
        final BlockState left_state = world.getBlockState(pos.relative(facing_left));
        if(left_state.is(this)) {
          final Direction dir = left_state.getValue(HORIZONTAL_FACING);
          if(dir == facing_left) {
            motion = Vec3.atLowerCornerOf(facing_left.getNormal()).scale((progress < 0.4) ? 0.7 : 1.5);
            outgoing = true;
          }
        }
      }
      // Itemframe sorting, only if not overridden using redstone.
      else if(front || up) {
        final Direction sorting_direction = itemFrameDiversion(world, pos, entity.getItem()).orElse(null);
        if(sorting_direction != null) {
          if((sorting_direction != block_facing) && (sorting_direction != block_facing.getOpposite()) && (sorting_direction != Direction.UP)) {
            if(sorting_direction == Direction.DOWN) {
              // check insertion
              if(!world.isClientSide()) {
                if(tryInsertItemEntity(world, pos, Direction.DOWN, entity)) {
                  entity.setDeltaMovement(entity.getDeltaMovement().scale(0.7));
                  return;
                }
              }
            } else {
              motion = Vec3.atLowerCornerOf(sorting_direction.getNormal());
              outgoing = true;
            }
          }
        }
      }

      // Motion update
      if(!outgoing && !front && !up) {
        motion = motion.scale(0);
      } else {
        if(!outgoing) {
          // Centering motion
          Vec3 centering_motion = new Vec3((block_facing.getAxis()== Direction.Axis.X ? 0 : -0.2*Math.signum(dp.x)), 0, (block_facing.getAxis()== Direction.Axis.Z ? 0 : -0.2*Math.signum(dp.z)));
          motion = motion.add(centering_motion);
          // Straight line speed-up/slow down
          final BlockState ahead_state = world.getBlockState(pos.relative(block_facing));
          if(ahead_state.is(this)) {
            final Direction dir = ahead_state.getValue(HORIZONTAL_FACING);
            if(dir == block_facing.getOpposite()) {
              motion = motion.scale(0.5);
            } else {
              motion = motion.scale(2);
            }
          }
        }
        double y_speed = 0.1;
        if(!up) {
          y_speed = -0.1 * Math.min(dp.y, 0.5);
          if((progress > 0.5) && front) check_insertion_front = true;
        } else {
          if(front) {
            motion = motion.scale(0.6);
          }
          if(world.getBlockState(pos.above()).is(this)) {
            if(progress > 0.5) y_speed = 0.2;
            if(outgoing) {
              motion = new Vec3(motion.x, 0, motion.z);
              y_speed = 0.08;
            }
          } else {
            if(front && (Math.abs(dp.get(block_facing.getClockWise().getAxis())) > 0.4)) {
              motion = new Vec3(motion.x, -0.1, motion.z);
              y_speed = 0.08;
            } else if(world.getBlockState(pos.above().relative(block_facing)).is(this)) {
              motion = motion.add(Vec3.atLowerCornerOf(block_facing.getNormal())).scale(0.5);
            }
            if(dp.y >= 0.4) check_insertion_up = true;
          }
        }
        if(motion.y < -0.1) motion = new Vec3(motion.x, -0.1, motion.z);
        motion = motion.scale(((!up) && (dp.y > 0)) ? (2e-2) : (7e-2)).add(0, y_speed, 0);
      }

      // Container insertion
      if((check_insertion_front || check_insertion_up) && (!world.isClientSide())) {
        if(!entity.getItem().isEmpty()){
          final Direction insertion_facing = check_insertion_up ? Direction.UP : block_facing;
          tryInsertItemEntity(world, pos, insertion_facing, entity);
        }
      }
      // Entity motion
      if(!up && state.getValue(WATERLOGGED)) motion = motion.add(0,-0.1,0);
      motion = entity.getDeltaMovement().scale(0.5).add(motion);
      entity.setDeltaMovement(motion);
      if(up) entity.resetFallDistance();
    }

    public void itchEntity(BlockState state, Level world, BlockPos pos, Entity entity)
    {
      if((world.getRandom().nextDouble() > 8e-3) || (!entity.isAlive()) || (!entity.isOnGround())
         || (world.isClientSide()) || (entity.isShiftKeyDown()) || (!entity.canChangeDimensions()) || (entity.isInWaterOrRain()) || (entity.hasImpulse)
         || (!(entity instanceof LivingEntity))
      ) {
        return;
      }
      if(entity instanceof Monster) {
        entity.hurt(DamageSource.CACTUS, 2f);
      } else if(entity instanceof Player) {
        if(world.getRandom().nextDouble() > 1e-1) return;
        entity.hurt(DamageSource.CACTUS, 0.1f);
      } else {
        entity.hurt(DamageSource.CACTUS, 0.0f);
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
        if(!frame.getItem().sameItemStackIgnoreDurability(match_stack)) continue;
        return Optional.of(frame.getDirection());
      }
      return Optional.empty();
    }

    private boolean tryInsertItemEntity(Level world, BlockPos pos, Direction insertion_facing, ItemEntity entity)
    {
      final IItemHandler ih = Inventories.itemhandler(world, pos.relative(insertion_facing), insertion_facing.getOpposite());
      if(ih == null) return false;
      ItemStack stack = entity.getItem().copy();
      final ItemStack remaining = Inventories.insert(ih, stack, false);
      if(remaining.getCount() >= stack.getCount()) return false;
      if(stack.isEmpty()) {
        entity.remove(Entity.RemovalReason.DISCARDED);
        return true;
      } else {
        entity.setItem(remaining);
        return false;
      }
    }
  }

}
