/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.blocks;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import wile.anthillinside.blocks.RedAntTrail.RedAntTrailBlock;
import wile.anthillinside.libmc.*;

import java.util.*;

import static wile.anthillinside.ModContent.references.*;


public class RedAntCoveredTrail
{
  private static final int max_transfer_stack_size = 8;

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntCoveredTrailBlock extends StandardBlocks.DirectedWaterLoggable implements StandardEntityBlocks.IStandardEntityBlock<RedAntCoveredTrailTileEntity>
  {
    public RedAntCoveredTrailBlock(long config, Properties props, AABB[] aabbs)
    {
      super(config, props.isRedstoneConductor((s,w,p)->false), aabbs);
      registerDefaultState(super.defaultBlockState().setValue(FACING, Direction.DOWN));
    }

    @Override
    public boolean isBlockEntityTicking(Level world, BlockState state)
    { return true; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    { return Shapes.block(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      BlockState state = super.getStateForPlacement(context).setValue(WATERLOGGED, false);
      if(state == null) return null;
      if(context.getPlayer().isShiftKeyDown()) {
        state = state.setValue(FACING, state.getValue(FACING).getOpposite());
      } else {
        boolean skip = false;
        BlockState clicked_state = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if(clicked_state.is(COVERED_TRAIL_BLOCK) || clicked_state.is(HIVE_BLOCK)) {
          final Direction adjacent_facing = clicked_state.getValue(FACING);
          if(adjacent_facing.getAxis() == context.getClickedFace().getAxis()) {
            state = state.setValue(FACING, adjacent_facing);
            skip = true;
          }
        }
        if(!skip) {
          final BlockState opposite_state = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace()));
          if(opposite_state.is(COVERED_TRAIL_BLOCK) || opposite_state.is(HIVE_BLOCK)) {
            final Direction adjacent_facing = opposite_state.getValue(FACING);
            if(adjacent_facing.getAxis() == context.getClickedFace().getAxis()) {
              state = state.setValue(FACING, adjacent_facing);
            }
          }
        }
      }
      return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos)
    { BlockEntity te = world.getBlockEntity(pos); return (te instanceof RedAntCoveredTrailTileEntity) ? ((RedAntCoveredTrailTileEntity)te).comparatorValue() : 0; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    { return new RedAntCoveredTrailTileEntity(pos, state); }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      if(world.isClientSide()) return;
      if((!stack.hasTag()) || (!stack.getOrCreateTag().contains("tedata"))) return;
      CompoundTag te_nbt = stack.getOrCreateTag().getCompound("tedata");
      if(te_nbt.isEmpty()) return;
      final BlockEntity te = world.getBlockEntity(pos);
      if(!(te instanceof RedAntCoveredTrailTileEntity)) return;
      ((RedAntCoveredTrailTileEntity)te).readnbt(te_nbt, false);
      te.setChanged();
    }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
    {
      if(world.isClientSide() || (!(te instanceof RedAntCoveredTrailTileEntity rte))) return Collections.emptyList();
      final ArrayList<ItemStack> drops = new ArrayList<>();
      drops.add(new ItemStack(asItem()));
      rte.inventory_range_.forEach(drops::add);
      rte.main_inventory_.clearContent();
      return drops;
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, LevelReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rayTraceResult)
    {
      if(!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
      if(world.isClientSide()) return InteractionResult.SUCCESS;
      final int co = this.getAnalogOutputSignal(state, world, pos);
      float min=0.9f, max=1.4f;
      float pitch = min + ((max-min) * co / 15);
      if(co == 0) pitch = 0.3f;
      world.playSound(null, pos, SoundEvents.BAMBOO_WOOD_PLACE, SoundSource.BLOCKS, 1f, pitch);
      return InteractionResult.CONSUME;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntCoveredTrailTileEntity extends StandardEntityBlocks.StandardBlockEntity implements SidedStorageBlockEntity
  {
    protected final Inventories.StorageInventory main_inventory_;
    protected final Inventories.InventoryRange inventory_range_;
    public static final int TICK_INTERVAL = 4;
    public static final int TICK_INTERVAL_FAST = 2;
    private int tick_timer_ = 0;

    public RedAntCoveredTrailTileEntity(BlockPos pos, BlockState state)
    {
      super(Registries.getBlockEntityTypeOfBlock(state.getBlock()), pos, state);
      main_inventory_ =  new Inventories.StorageInventory(this, 1);
      inventory_range_ = new Inventories.InventoryRange(main_inventory_, 0, 1);
    }

    //---- Fabric/Forge item handling
    @SuppressWarnings("all") @Nullable private InventoryStorage storage_wrapper_ = null;
    @SuppressWarnings("all") @Override @Nullable
    public Storage<ItemVariant> getItemStorage(@Nullable Direction side)
    {
      if(side == getBlockState().getValue(RedAntCoveredTrailBlock.FACING)) return null;
      if(storage_wrapper_==null) storage_wrapper_ = InventoryStorage.of(inventory_range_, null);
      return storage_wrapper_;
      /*
        Forge:
          item_handler_ = Inventories.MappedItemHandler.createGenericHandler(
            main_inventory_,
            (slot, stack)->(slot >= inventory_range_.offset()) && (slot < (inventory_range_.offset()+inventory_range_.size())),
            (slot, stack)->(slot >= inventory_range_.offset()) && (slot < (inventory_range_.offset()+inventory_range_.size())) && insertionAllowed(inventory_range_.offset()+slot, stack)
          );
      */
    }
    //----

    public CompoundTag clear_getnbt()
    {
      CompoundTag nbt = new CompoundTag();
      if(!main_inventory_.isEmpty()) {
        writenbt(nbt, false);
      } else {
        main_inventory_.clearContent();
      }
      return nbt;
    }

    public void readnbt(CompoundTag nbt, boolean update_packet)
    { main_inventory_.load(nbt); }

    protected void writenbt(CompoundTag nbt, boolean update_packet)
    { main_inventory_.save(nbt); }

    public int comparatorValue()
    {
      if(main_inventory_.isEmpty()) return 0;
      final int max = main_inventory_.getItem(0).getMaxStackSize();
      final int use = main_inventory_.getItem(0).getCount();
      if(use == 0) return 0;
      if(use == max) return 15;
      return 1 + (14 * use / max);
    }

    // BlockEntity --------------------------------------------------------------------------------------------

    @Override
    public void load(CompoundTag nbt)
    { super.load(nbt); readnbt(nbt, false); }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    { super.saveAdditional(nbt); writenbt(nbt, false); }

    // Tick -------------------------------------------------------------------------------------------------

    @Override
    public void tick()
    {
      // Tick timing
      if(--tick_timer_ > 0) return;
      tick_timer_ = TICK_INTERVAL;
      boolean dirty = checkItemInput();
      if(checkItemOutput()) { dirty = true; tick_timer_ = TICK_INTERVAL_FAST; }
      if(dirty) setChanged();
    }

    // TE specific ------------------------------------------------------------------------------------------

    protected boolean insertionAllowed(int index, ItemStack stack)
    {
      if(index >= inventory_range_.size()) return false;
      if(inventory_range_.getItem(index).isEmpty()) return true;
      return inventory_range_.getItem(index).getItem() == stack.getItem();
    }

    protected ItemStack insert(ItemStack stack)
    { return inventory_range_.insert(stack.copy()); }

    private boolean checkItemOutput()
    {
      final Direction output_facing = getBlockState().getValue(RedAntCoveredTrailBlock.FACING);
      final BlockPos output_position = getBlockPos().relative(output_facing);
      final Inventories.ItemPort ih = Inventories.ItemPort.of(getLevel(), output_position, output_facing.getOpposite(), true);
      if(ih != Inventories.ItemPort.EMPTY) {
        if(ih.allowsInsertion()) {
          for(int slot=0; slot<inventory_range_.size(); ++slot) {
            ItemStack ostack = inventory_range_.getItem(slot);
            if(ostack.isEmpty()) continue;
            final ItemStack stack = ostack.copy();
            if(stack.getCount() > max_transfer_stack_size) stack.setCount(max_transfer_stack_size);
            final ItemStack remaining = ih.insert(stack);
            if(remaining.getCount() == stack.getCount()) continue;
            final int n_inserted = stack.getCount() - remaining.getCount();
            inventory_range_.getItem(slot).shrink(n_inserted);
            return true; // TE dirty.
          }
        } else {
          final BlockState trail_state = getLevel().getBlockState(output_position);
          if(!trail_state.is(TRAIL_BLOCK)) return false;
          if((output_facing == trail_state.getValue(RedAntTrailBlock.HORIZONTAL_FACING).getOpposite()) || (output_facing==Direction.DOWN && trail_state.getValue(RedAntTrailBlock.UP))) return false;
          Inventories.dropStack(getLevel(), Vec3.atCenterOf(output_position).add(0,-.4,0), inventory_range_.extract(1), new Vec3(0, -0.2, 0), 0.1, 0.1);
          return true;
        }
      } else {
        final BlockState state = getLevel().getBlockState(output_position);
        if(state.isCollisionShapeFullBlock(getLevel(), output_position)) return false;
        ItemStack stack = inventory_range_.extract(1, true);
        Vec3 drop_pos = Vec3.atCenterOf(output_position).add(Vec3.atLowerCornerOf(output_facing.getOpposite().getNormal()).scale(0.1));
        Vec3 speed = Vec3.atLowerCornerOf(output_facing.getNormal()).scale(0.02);
        Inventories.dropStack(getLevel(), drop_pos, stack, speed, 0.2,0.02);
      }
      return false;
    }

    private boolean checkItemInput()
    {
      final Direction input_facing = getBlockState().getValue(RedAntCoveredTrailBlock.FACING).getOpposite();
      final Inventories.ItemPort ih = Inventories.ItemPort.of(getLevel(), getBlockPos().relative(input_facing), input_facing.getOpposite(), true);
      if((ih == Inventories.ItemPort.EMPTY) || (!ih.allowsExtraction())) return false;
      return inventory_range_.iterate((slot,ref_stack)->{
        if((ref_stack.getCount() >= ref_stack.getMaxStackSize()) || (ref_stack.getCount() >= (inventory_range_.getMaxStackSize()))) return false;
        if(ref_stack.isEmpty()) {
          final ItemStack fetched = ih.extract(ref_stack.isEmpty() ? null : ref_stack, max_transfer_stack_size, false);
          if(!fetched.isEmpty()) { insert(fetched); return true; }
        } else {
          final int limit = Math.min(max_transfer_stack_size, ref_stack.getMaxStackSize() - ref_stack.getCount());
          final ItemStack fetched = ih.extract(ref_stack, limit, false);
          if(!fetched.isEmpty()) { insert(fetched); return true; }
        }
        return false;
      });
    }
  }
}
