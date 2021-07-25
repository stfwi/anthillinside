/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.blocks;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.schedule.Activity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.state.StateContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.IItemHandler;
import wile.anthillinside.ModContent;
import wile.anthillinside.libmc.blocks.StandardBlocks;
import wile.anthillinside.libmc.detail.Auxiliaries;
import wile.anthillinside.libmc.detail.Inventories;

import javax.annotation.Nullable;
import java.util.*;


import net.minecraft.block.AbstractBlock;

public class RedAntTrail
{
  private static double speed_modifier = 1.0;

  public static void on_config(int speed_percent)
  {
    speed_modifier = ((double)MathHelper.clamp((speed_percent), 25, 200))/100;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntTrailBlock extends StandardBlocks.HorizontalWaterLoggable implements StandardBlocks.IBlockItemFactory
  {
    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");

    public RedAntTrailBlock(long config, AbstractBlock.Properties builder)
    {
      super(config, builder, (states)->{
        final HashMap<BlockState,VoxelShape> shapes = new HashMap<>();
        final AxisAlignedBB base_aabb = Auxiliaries.getPixeledAABB(0,0,0,16,0.2,16);
        final AxisAlignedBB up_aabb   = Auxiliaries.getPixeledAABB(0,0,0,16,16,0.2);
        for(BlockState state:states) {
          final Direction facing = state.getValue(HORIZONTAL_FACING);
          VoxelShape shape = VoxelShapes.empty();
          if(state.getValue(UP)) {
            shape = VoxelShapes.joinUnoptimized(shape, VoxelShapes.create(Auxiliaries.getRotatedAABB(up_aabb, facing, true)), IBooleanFunction.OR);
          }
          if(state.getValue(FRONT) || (!state.getValue(UP))) {
            shape = VoxelShapes.joinUnoptimized(shape, VoxelShapes.create(Auxiliaries.getRotatedAABB(base_aabb, facing, true)), IBooleanFunction.OR);
          }
          shapes.putIfAbsent(state, shape);
        }
        return shapes;
      });
      registerDefaultState(super.defaultBlockState().setValue(FRONT, true).setValue(UP, false).setValue(LEFT, false).setValue(RIGHT, false));
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
    public List<ItemStack> dropList(BlockState state, World world, final TileEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(asItem())); }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(FRONT, LEFT, RIGHT, UP); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return state;
      if((!state.getValue(UP)) && (context.getClickedFace().getAxis().isVertical()) && (!Block.canSupportRigidBlock(context.getLevel(), context.getClickedPos().below()))) return null;
      return updatedState(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
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
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, IWorld world, BlockPos pos, BlockPos facingPos)
    {
      if(!(world instanceof World)) return state;
      if(canSurvive(state, world, pos)) return updatedState(state, world, pos);
      return Blocks.AIR.defaultBlockState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, IWorldReader world, BlockPos pos)
    {
      if(Block.isFaceFull(world.getBlockState(pos.below()).getShape(world, pos.below()), Direction.UP)) return true;
      if(!state.getValue(UP)) return false;
      Direction facing = state.getValue(HORIZONTAL_FACING);
      if(!Block.isFaceFull(world.getBlockState(pos.relative(facing)).getShape(world, pos.relative(facing)), facing.getOpposite())) return false;
      return true;
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rtr)
    {
      Direction dir = rtr.getDirection().getOpposite();
      return world.getBlockState(pos.relative(dir)).use(world, player, hand, new BlockRayTraceResult(
        rtr.getLocation(), rtr.getDirection(), pos.relative(dir), false
      ));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, World world, BlockPos pos, Entity entity)
    {
      if(entity instanceof ItemEntity) {
        moveEntity(state, world, pos, entity);
        if(state.getValue(UP) && (!world.getBlockTicks().hasScheduledTick(pos, this))) {
          world.getBlockTicks().scheduleTick(pos, this, 60);
        }
      } else if(entity instanceof LivingEntity) {
        itchEntity(state, world, pos, entity);
      }
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rand)
    {
      if(!state.getValue(UP)) return;
      final BlockState st = world.getBlockState(pos.above());
      if(st==null || st.is(this)) return;
      final List<ItemEntity> entities = world.getEntitiesOfClass(ItemEntity.class, new AxisAlignedBB(pos.above()).expandTowards(0,-0.2,0), Entity::isAlive);
      final Vector3d v = Vector3d.atLowerCornerOf(state.getValue(HORIZONTAL_FACING).getNormal()).add(0,1,0).scale(0.1);
      for(ItemEntity entity:entities) entity.setDeltaMovement(v);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
    { world.setBlock(pos, updatedState(state, world, pos), 2); }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, IWorldReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side)
    { return true; }

    @Override
    public boolean isPathfindable(BlockState state, IBlockReader world, BlockPos pos, PathType type)
    { return (!state.getValue(UP)) || super.isPathfindable(state, world, pos, type); }

    //------------------------------------------------------------------------------------------------------

    public BlockState updatedState(@Nullable BlockState state, IWorld world, BlockPos pos)
    {
      if((state == null) || (!(world instanceof World))) return state;
      final Direction facing = state.getValue(HORIZONTAL_FACING);
      boolean down_solid = Block.canSupportRigidBlock(world, pos.below());
      boolean up_is_cube = world.getBlockState(pos.above()).isRedstoneConductor(world, pos.above());
      final boolean up = isFaceFull(world.getBlockState(pos.relative(facing)).getShape(world, pos.relative(facing)), facing.getOpposite())
        && ((!down_solid) || (world.getBlockState(pos.above()).is(this)) || ((!up_is_cube) && (world.getBlockState(pos.relative(facing).above()).is(this))));
      boolean left = false, right = false;
      boolean front = down_solid;
      if(((World)world).hasNeighborSignal(pos)) {
        {
          final BlockState right_state = world.getBlockState(pos.relative(facing.getClockWise()));
          if(right_state.is(this)) {
            if(right_state.getValue(HORIZONTAL_FACING) == facing.getClockWise()) right = true;
          }
        }
        {
          final BlockState left_state = world.getBlockState(pos.relative(facing.getCounterClockWise()));
          if(left_state.is(this)) {
            if(left_state.getValue(HORIZONTAL_FACING) == facing.getCounterClockWise()) left = true;
          }
        }
        if(!right && !left) {
          front = false;
        }
      } else if(!right && !left && !up) {
        front = true;
      }
      state = state.setValue(FRONT, front).setValue(RIGHT, right).setValue(LEFT, left).setValue(UP, up);
      return state;
    }

    public void moveEntity(BlockState state, World world, BlockPos pos, Entity any_entity)
    {
      if((!any_entity.isAlive()) || (!(any_entity instanceof ItemEntity))) return;
      final ItemEntity entity = (ItemEntity)any_entity;
      if(entity.getItem().isEmpty() || entity.getItem().getItem() == ModContent.ANTS_ITEM) return;
      final boolean up = state.getValue(UP);
      if(!up && !entity.isOnGround()) return;
      double speed = 7e-2 * speed_modifier;
      final Vector3d dp = entity.position().subtract(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).scale(2);
      if((!up) && (dp.y > 0)) speed *= 0.2;
      boolean outgoing = false, check_insertion_front = false, check_insertion_up = false;
      final boolean front = state.getValue(FRONT);
      boolean right = state.getValue(RIGHT);
      boolean left = state.getValue(LEFT);
      final Direction block_facing = state.getValue(HORIZONTAL_FACING);
      final Optional<Direction> sorting_diversion = (left || right || (!front && !up)) ? Optional.empty() : itemFrameDiversion(world, pos, entity.getItem());
      Vector3d motion = Vector3d.atLowerCornerOf(block_facing.getNormal());
      if(sorting_diversion.isPresent()) {
        final Direction sorting_direction = sorting_diversion.get();
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
            motion = Vector3d.atLowerCornerOf(sorting_direction.getNormal());
            right = false;
            left = false;
            outgoing = true;
          }
        }
      }
      if(right) {
        final Direction facing_right = block_facing.getClockWise();
        final BlockState right_state = world.getBlockState(pos.relative(facing_right));
        if(right_state.is(this)) {
          final Direction dir = right_state.getValue(HORIZONTAL_FACING);
          if(dir == facing_right) {
            motion = Vector3d.atLowerCornerOf(facing_right.getNormal());
            outgoing = true;
          }
        }
      } else if(left) {
        final Direction facing_left  = block_facing.getCounterClockWise();
        final BlockState left_state = world.getBlockState(pos.relative(facing_left));
        if(left_state.is(this)) {
          final Direction dir = left_state.getValue(HORIZONTAL_FACING);
          if(dir == facing_left) {
            motion = Vector3d.atLowerCornerOf(facing_left.getNormal());
            outgoing = true;
          }
        }
      }
      {
        if(!outgoing && !front && !up) {
          motion = motion.scale(0);
        } else {
          if(!outgoing) {
            Vector3d centering_motion = new Vector3d((block_facing.getAxis()==Axis.X ? 0 : -0.2*Math.signum(dp.x)), 0, (block_facing.getAxis()==Axis.Z ? 0 : -0.1*Math.signum(dp.z)));
            if(up) centering_motion.scale(2);
            motion = motion.add(centering_motion);
          }
          final BlockState ahead_state = world.getBlockState(pos.relative(block_facing));
          if(!outgoing && ahead_state.is(this)) {
            final Direction dir = ahead_state.getValue(HORIZONTAL_FACING);
            if(dir == block_facing) {
              motion = motion.scale(2);
            } else if(dir == block_facing.getOpposite()) {
              motion = motion.scale(0.5);
            } else if(ahead_state.getValue(UP)) {
              motion = motion.scale(2);
            }
          }
          final double progress = dp.get(block_facing.getAxis()) * Vector3d.atLowerCornerOf(block_facing.getNormal()).get(block_facing.getAxis());
          double y_speed = -0.1 * Math.min(dp.y, 0.5);
          if(!up) {
            if((progress > 0.7) && front) check_insertion_front = true;
          } else {
            if(front && (dp.y < 0.3) && (progress < 0.6)) {
              y_speed = 0.08;
            } else if((progress > 0.7) && (world.getBlockState(pos.above().relative(block_facing)).is(this))) {
              motion = motion.scale(1.2);
              y_speed = 0.14;
            } else {
              y_speed = 0.1;
            }
            if((dp.y >= 0.4) && (!world.getBlockState(pos.above()).is(this))) {
              check_insertion_up = true;
            }
          }
          if(motion.y < -0.1) motion = new Vector3d(motion.x, -0.1, motion.z);
          motion = motion.scale(speed).add(0, y_speed, 0);
        }
      }
      if((check_insertion_front || check_insertion_up) && (!world.isClientSide())) {
        if(!entity.getItem().isEmpty()){
          final Direction insertion_facing = check_insertion_up ? Direction.UP : block_facing;
          tryInsertItemEntity(world, pos, insertion_facing, entity);
        }
      }
      if(state.getValue(WATERLOGGED)) motion.add(0,-0.1,0);
      motion = entity.getDeltaMovement().scale(0.5).add(motion);
      entity.setDeltaMovement(motion);
    }

    public void itchEntity(BlockState state, World world, BlockPos pos, Entity entity)
    {
      if((world.getRandom().nextDouble() > 8e-3) || (!entity.isAlive()) || (!entity.isOnGround())
         || (world.isClientSide()) || (entity.isShiftKeyDown()) || (!entity.canChangeDimensions()) || (entity.isInWaterOrRain()) || (entity.hasImpulse)
         || (!(entity instanceof LivingEntity))
      ) {
        return;
      }
      if(entity instanceof MonsterEntity) {
        entity.hurt(DamageSource.CACTUS, 2f);
      } else if(entity instanceof PlayerEntity) {
        if(world.getRandom().nextDouble() > 1e-1) return;
        entity.hurt(DamageSource.CACTUS, 0.1f);
      } else {
        entity.hurt(DamageSource.CACTUS, 0.0f);
        if(entity instanceof VillagerEntity) {
          ((VillagerEntity)entity).getBrain().setActiveActivityIfPossible(Activity.PANIC);
        } else if(entity instanceof AnimalEntity) {
          ((AnimalEntity)entity).getBrain().setActiveActivityIfPossible(Activity.PANIC);
        }
      }
    }

    private Optional<Direction> itemFrameDiversion(World world, BlockPos pos, ItemStack match_stack)
    {
      List<ItemFrameEntity> frames = world.getEntitiesOfClass(ItemFrameEntity.class, new AxisAlignedBB(pos));
      if(frames.isEmpty()) return Optional.empty();
      for(ItemFrameEntity frame:frames) {
        if(!frame.getItem().sameItemStackIgnoreDurability(match_stack)) continue;
        return Optional.of(frame.getDirection());
      }
      return Optional.empty();
    }

    private boolean tryInsertItemEntity(World world, BlockPos pos, Direction insertion_facing, ItemEntity entity)
    {
      final IItemHandler ih = Inventories.itemhandler(world, pos.relative(insertion_facing), insertion_facing.getOpposite());
      if(ih == null) return false;
      ItemStack stack = entity.getItem().copy();
      final ItemStack remaining = Inventories.insert(ih, stack, false);
      if(remaining.getCount() >= stack.getCount()) return false;
      if(stack.isEmpty()) {
        entity.remove();
        return true;
      } else {
        entity.setItem(remaining);
        return false;
      }
    }
  }

}
