/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.blocks;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.crafting.AbstractCookingRecipe;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.state.StateContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.inventory.*;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import wile.anthillinside.ModAnthillInside;
import wile.anthillinside.ModContent;
import wile.anthillinside.blocks.RedAntTrail.RedAntTrailBlock;
import wile.anthillinside.libmc.blocks.StandardBlocks;
import wile.anthillinside.libmc.detail.Crafting;
import wile.anthillinside.libmc.detail.Inventories;
import wile.anthillinside.libmc.detail.Networking;
import wile.anthillinside.libmc.detail.TooltipDisplay;
import wile.anthillinside.libmc.ui.Containers;
import wile.anthillinside.libmc.ui.Containers.StorageSlot;
import wile.anthillinside.libmc.ui.Guis;
import wile.anthillinside.libmc.ui.Guis.ContainerGui;
import wile.anthillinside.libmc.ui.Guis.Coord2d;
import wile.anthillinside.libmc.ui.Guis.HorizontalProgressBar;
import wile.anthillinside.libmc.util.IntegralBitSet;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;


public class RedAntHive
{
  private static final Item ANTS_ITEM = ModContent.ANTS_ITEM;
  private static final Item RED_SUGAR_ITEM = ModContent.RED_SUGAR_ITEM;
  private static int hive_drop_probability_percent = 3;
  private static int sugar_boost_time_s = 5;
  private static int hive_growth_latency_s = 120;
  private static int normal_processing_speed_ant_count_percent = 100;
  private static final HashMap<Item, Object> processing_command_item_mapping = new HashMap<>();

  public static void on_config(int ore_minining_spawn_probability_percent, int ant_speed_scaler_percent, int sugar_time_s, int growth_latency_s)
  {
    hive_drop_probability_percent = MathHelper.clamp(ore_minining_spawn_probability_percent, 1, 99);
    normal_processing_speed_ant_count_percent = MathHelper.clamp(ant_speed_scaler_percent, 10, 190);
    sugar_boost_time_s = MathHelper.clamp(sugar_time_s, 1, 60);
    hive_growth_latency_s = MathHelper.clamp(growth_latency_s, 10, 600);
    processing_command_item_mapping.clear();
    processing_command_item_mapping.put(Items.CRAFTING_TABLE, IRecipeType.CRAFTING);
    processing_command_item_mapping.put(Items.FURNACE, IRecipeType.SMELTING);
    processing_command_item_mapping.put(Items.BLAST_FURNACE, IRecipeType.BLASTING);
    processing_command_item_mapping.put(Items.SMOKER, IRecipeType.SMOKING);
    processing_command_item_mapping.put(Items.HOPPER, Items.HOPPER);
    processing_command_item_mapping.put(Items.COMPOSTER, Items.COMPOSTER);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // External events
  //--------------------------------------------------------------------------------------------------------------------

  public static void onGlobalPlayerBlockBrokenEvent(BlockState state, IWorld iworld, BlockPos pos, PlayerEntity player)
  {
    if((!state.isIn(Blocks.REDSTONE_ORE)) || (iworld.isRemote()) || !(iworld instanceof World)) return;
    if(iworld.getRandom().nextInt(100) >= hive_drop_probability_percent) return;
    World world = (World)iworld;
    Inventories.dropStack(world, Vector3d.copyCentered(pos), new ItemStack(ModContent.HIVE_BLOCK.asItem()));
    world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.BLOCKS, 1f,1.4f);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveBlock extends StandardBlocks.DirectedWaterLoggable
  {
    public RedAntHiveBlock(long config, Block.Properties builder, AxisAlignedBB[] aabbs)
    {
      super(config, builder, aabbs);
      setDefaultState(super.getDefaultState().with(FACING,Direction.DOWN));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context)
    { return VoxelShapes.fullCube(); }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    { super.fillStateContainer(builder); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return null;
      if(!context.getPlayer().isSneaking()) state = state.with(FACING, Direction.DOWN);
      return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(BlockState blockState, World world, BlockPos pos)
    { TileEntity te = world.getTileEntity(pos); return (te instanceof RedAntHiveTileEntity) ? ((RedAntHiveTileEntity)te).comparatorValue() : 0; }

    @Override
    public boolean hasTileEntity(BlockState state)
    { return true; }

    @Override
    @Nullable
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    { return new RedAntHiveTileEntity(); }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      if(world.isRemote()) return;
      if((!stack.hasTag()) || (!stack.getTag().contains("tedata"))) return;
      CompoundNBT te_nbt = stack.getTag().getCompound("tedata");
      if(te_nbt.isEmpty()) return;
      final TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof RedAntHiveTileEntity)) return;
      ((RedAntHiveTileEntity)te).readnbt(te_nbt, false);
      ((RedAntHiveTileEntity)te).markDirty();
    }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, final TileEntity te, boolean explosion)
    {
      if(world.isRemote() || (!(te instanceof RedAntHiveTileEntity))) return Collections.emptyList();
      final CompoundNBT te_nbt = ((RedAntHiveTileEntity)te).clear_getnbt();
      ItemStack stack = new ItemStack(asItem());
      if(!te_nbt.isEmpty()) {
        final CompoundNBT nbt = new CompoundNBT();
        nbt.put("tedata", te_nbt);
        stack.setTag(nbt);
      }
      return Collections.singletonList(stack);
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult)
    {
      if(world.isRemote()) return ActionResultType.SUCCESS;
      final TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof RedAntHiveTileEntity)) return ActionResultType.FAIL;
      if((!(player instanceof ServerPlayerEntity) && (!(player instanceof FakePlayer)))) return ActionResultType.FAIL;
      NetworkHooks.openGui((ServerPlayerEntity)player,(INamedContainerProvider)te);
      return ActionResultType.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
    {
      if(!(world instanceof World) || (((World) world).isRemote)) return;
      TileEntity te = world.getTileEntity(pos);
      if(!(te instanceof RedAntHiveTileEntity)) return;
      ((RedAntHiveTileEntity)te).block_updated();
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, IWorldReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side)
    { return true; }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveTileEntity extends TileEntity implements ITickableTileEntity, INameable, INamedContainerProvider
  {
    // Inventory sections ----------------------------------------------------------------------------------------------
    public static final int LEFT_STORAGE_NUM_SLOTS   = 10;
    public static final int LEFT_STORAGE_NUM_ROWS    = 5;
    public static final int LEFT_STORAGE_START       = 0;
    public static final int RIGHT_STORAGE_NUM_SLOTS  = 10;
    public static final int RIGHT_STORAGE_NUM_ROWS   = 5;
    public static final int RIGHT_STORAGE_START      = LEFT_STORAGE_START+LEFT_STORAGE_NUM_SLOTS;
    public static final int ANT_STORAGE_NUM_SLOTS    = 3;
    public static final int ANT_STORAGE_NUM_ROWS     = 1;
    public static final int ANT_STORAGE_START        = RIGHT_STORAGE_START+RIGHT_STORAGE_NUM_SLOTS;
    public static final int CMD_STORAGE_NUM_SLOTS    = 1;
    public static final int CMD_STORAGE_NUM_ROWS     = 1;
    public static final int CMD_STORAGE_START        = ANT_STORAGE_START+ANT_STORAGE_NUM_SLOTS;
    public static final int GRID_STORAGE_NUM_SLOTS   = 9;
    public static final int GRID_STORAGE_NUM_ROWS    = 3;
    public static final int GRID_STORAGE_START       = CMD_STORAGE_START+CMD_STORAGE_NUM_SLOTS;
    public static final int INP_STORAGE_NUM_SLOTS    = 1;
    public static final int INP_STORAGE_NUM_ROWS     = 1;
    public static final int INP_STORAGE_START        = GRID_STORAGE_START+GRID_STORAGE_NUM_SLOTS;
    public static final int OUT_STORAGE_NUM_SLOTS    = 1;
    public static final int OUT_STORAGE_NUM_ROWS     = 1;
    public static final int OUT_STORAGE_START        = INP_STORAGE_START+INP_STORAGE_NUM_SLOTS;
    public static final int CMD_RESULT_NUM_SLOTS     = 1;
    public static final int CMD_RESULT_NUM_ROWS      = 1;
    public static final int CMD_RESULT_START         = OUT_STORAGE_START+OUT_STORAGE_NUM_SLOTS;
    public static final int CMD_CACHE_NUM_SLOTS      = 9;
    public static final int CMD_CACHE_NUM_ROWS       = 3;
    public static final int CMD_CACHE_START          = CMD_RESULT_START+CMD_RESULT_NUM_SLOTS;
    public static final int LEFT_FILTER_NUM_SLOTS    = LEFT_STORAGE_NUM_SLOTS;
    public static final int LEFT_FILTER_NUM_ROWS     = LEFT_STORAGE_NUM_ROWS;
    public static final int LEFT_FILTER_START        = CMD_CACHE_START+CMD_CACHE_NUM_SLOTS;
    public static final int NUM_MAIN_INVENTORY_SLOTS = LEFT_FILTER_START+LEFT_FILTER_NUM_SLOTS;

    protected final Inventories.StorageInventory main_inventory_;
    protected final Inventories.InventoryRange left_storage_slot_range_;
    protected final Inventories.InventoryRange left_filter_slot_range_;
    protected final Inventories.InventoryRange right_storage_slot_range_;
    protected final Inventories.InventoryRange ant_storage_slot_range_;
    protected final Inventories.InventoryRange grid_storage_slot_range_;
    protected final Inventories.InventoryRange cache_slot_range_;
    protected LazyOptional<? extends IItemHandler> item_handler_;

    // TE state --------------------------------------------------------------------------------------------------------

    public static class StateFlags extends IntegralBitSet
    {
      public static final long mask_powered       = (((long)1)<<0);
      public static final long mask_sugared       = (((long)1)<<1);
      public static final long mask_nofuel        = (((long)1)<<2);
      public static final long mask_norecipe      = (((long)1)<<3);
      public static final long mask_noingr        = (((long)1)<<4);
      public static final long mask_nopassthrough = (((long)1)<<5);
      public static final long mask_filteredinsert= (((long)1)<<6);

      public StateFlags(int v)             { super(v); }
      public boolean powered()             { return bit(0); }
      public boolean sugared()             { return bit(1); }
      public boolean nofuel()              { return bit(2); }
      public boolean norecipe()            { return bit(3); }
      public boolean noingr()              { return bit(4); }
      public boolean nopassthrough()       { return bit(5); }
      public boolean filteredinsert()      { return bit(6); }
      public void powered(boolean v)       { bit(0, v); }
      public void sugared(boolean v)       { bit(1, v); }
      public void nofuel(boolean v)        { bit(2, v); }
      public void norecipe(boolean v)      { bit(3, v); }
      public void noingr(boolean v)        { bit(4, v); }
      public void nopassthrough(boolean v) { bit(5, v); }
      public void filteredinsert(boolean v){ bit(6, v); }
    }

    public static final int NUM_OF_FIELDS            =  3;
    public static final int TICK_INTERVAL            =  8;
    public static final int SLOW_INTERVAL            =  2;
    private StateFlags state_flags_ = new StateFlags(0);
    private String last_recipe_ = "";
    private int colony_growth_progress_ = 0;
    private int sugar_ticks_ = 0;
    private boolean can_use_sugar_ = false;
    private int ant_count_ = 0;
    private double ant_speed_ = 0;
    private double progress_ = 0;
    private int max_progress_ = 0;
    private int fuel_left_ = 0;
    private int tick_timer_ = 0;
    private int slow_timer_ = 0; // performance timer for slow tasks

    public static final BiPredicate<Integer, ItemStack> main_inventory_validator() {
      return (index, stack) -> {
        if(stack.getItem() == ModContent.HIVE_BLOCK.asItem()) {
          return false;
        } else if((index >= ANT_STORAGE_START) && (index < ANT_STORAGE_START+ANT_STORAGE_NUM_SLOTS)) {
          return (stack.getItem() == ANTS_ITEM);
        } else if((index >= CMD_STORAGE_START) && (index < CMD_STORAGE_START+CMD_STORAGE_NUM_SLOTS)) {
          return (processing_command_item_mapping.containsKey(stack.getItem()));
        } else if((index >= GRID_STORAGE_START) && (index < GRID_STORAGE_START+GRID_STORAGE_NUM_SLOTS)) {
          return (stack.getItem() != ANTS_ITEM);
        } else if((index >= INP_STORAGE_START) && (index < INP_STORAGE_START+INP_STORAGE_NUM_SLOTS)) {
          return (stack.getItem() == Items.HOPPER);
        } else if((index >= OUT_STORAGE_START) && (index < OUT_STORAGE_START+OUT_STORAGE_NUM_SLOTS)) {
          return (stack.getItem() == Items.HOPPER) || (stack.getItem() == Items.DROPPER) || (stack.getItem() == Items.DISPENSER);
        } else if((index >= CMD_RESULT_START) && (index < CMD_RESULT_START+CMD_RESULT_NUM_SLOTS)) {
          return false;
        }
        return true;
      };
    }

    // TE  -------------------------------------------------------------------------------------------------------------

    public RedAntHiveTileEntity()
    { this(ModContent.TET_HIVE); }

    public RedAntHiveTileEntity(TileEntityType<?> te_type)
    {
      super(te_type);
      main_inventory_           =  new Inventories.StorageInventory(this, NUM_MAIN_INVENTORY_SLOTS).setValidator(main_inventory_validator());
      left_storage_slot_range_  =  new Inventories.InventoryRange(main_inventory_, LEFT_STORAGE_START , LEFT_STORAGE_NUM_SLOTS, LEFT_STORAGE_NUM_ROWS);
      right_storage_slot_range_ =  new Inventories.InventoryRange(main_inventory_, RIGHT_STORAGE_START, RIGHT_STORAGE_NUM_SLOTS, RIGHT_STORAGE_NUM_ROWS);
      ant_storage_slot_range_   =  new Inventories.InventoryRange(main_inventory_, ANT_STORAGE_START, ANT_STORAGE_NUM_SLOTS, ANT_STORAGE_NUM_ROWS);
      grid_storage_slot_range_  = (new Inventories.InventoryRange(main_inventory_, GRID_STORAGE_START, GRID_STORAGE_NUM_SLOTS, GRID_STORAGE_NUM_ROWS)).setMaxStackSize(1);
      cache_slot_range_         = (new Inventories.InventoryRange(main_inventory_, CMD_CACHE_START, CMD_CACHE_NUM_SLOTS, CMD_CACHE_NUM_ROWS));
      left_filter_slot_range_   = (new Inventories.InventoryRange(main_inventory_, LEFT_FILTER_START, LEFT_FILTER_NUM_SLOTS, LEFT_FILTER_NUM_ROWS)).setMaxStackSize(1);
      item_handler_ = Inventories.MappedItemHandler.createGenericHandler(
        main_inventory_,
        (slot, stack)->(slot >= right_storage_slot_range_.offset) && (slot < (right_storage_slot_range_.offset+right_storage_slot_range_.size)),
        (slot, stack)->(slot >= left_storage_slot_range_.offset) && (slot < (left_storage_slot_range_.offset+left_storage_slot_range_.size)) && insertionAllowed(left_storage_slot_range_.offset+slot, stack)
      );
      state_flags_.nopassthrough(true);
    }

    public CompoundNBT clear_getnbt()
    {
      CompoundNBT nbt = new CompoundNBT();
      if(!main_inventory_.isEmpty()) {
        writenbt(nbt, false);
      } else {
        main_inventory_.clear();
      }
      return nbt;
    }

    public void readnbt(CompoundNBT nbt, boolean update_packet)
    {
      main_inventory_.load(nbt);
      state_flags_.value(nbt.getLong("state_flags"));
      progress_ = nbt.getDouble("progress");
      max_progress_ = nbt.getInt("max_progress");
      colony_growth_progress_ = nbt.getInt("colony_growth_progress");
      sugar_ticks_ = nbt.getInt("sugar_ticks");
      fuel_left_ = nbt.getInt("fuel_left");
      last_recipe_ = nbt.getString("last_recipe");
    }

    protected void writenbt(CompoundNBT nbt, boolean update_packet)
    {
      main_inventory_.save(nbt);
      nbt.putLong("state_flags", state_flags_.value());
      nbt.putDouble("progress", progress_);
      nbt.putInt("max_progress", max_progress_);
      nbt.putInt("colony_growth_progress", colony_growth_progress_);
      nbt.putInt("sugar_ticks", sugar_ticks_);
      nbt.putInt("fuel_left", fuel_left_);
      nbt.putString("last_recipe", last_recipe_);
    }

    public void block_updated()
    { tick_timer_ = Math.min(tick_timer_, 2); }

    public int comparatorValue()
    { return 0; }

    // TileEntity ------------------------------------------------------------------------------

    @Override
    public void read(BlockState state, CompoundNBT nbt)
    { super.read(state, nbt); readnbt(nbt, false); }

    @Override
    public CompoundNBT write(CompoundNBT nbt)
    { super.write(nbt); writenbt(nbt, false); return nbt; }

    @Override
    public void remove()
    { super.remove(); item_handler_.invalidate(); }

    @Override
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable Direction facing)
    {
      if(capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return item_handler_.cast();
      return super.getCapability(capability, facing);
    }

    // INamable ----------------------------------------------------------------------------------------------

    @Override
    public ITextComponent getName()
    { final Block block=getBlockState().getBlock(); return (block!=null) ? (new TranslationTextComponent(block.getTranslationKey())) : (new StringTextComponent("Hive")); }

    @Override
    public boolean hasCustomName()
    { return false; }

    @Override
    public ITextComponent getCustomName()
    { return getName(); }

    // INamedContainerProvider ------------------------------------------------------------------------------

    @Override
    public ITextComponent getDisplayName()
    { return INameable.super.getDisplayName(); }

    @Override
    public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player )
    { return new RedAntHiveContainer(id, inventory, main_inventory_, IWorldPosCallable.of(world, pos), fields); }

    protected final IIntArray fields = new IntArray(RedAntHiveTileEntity.NUM_OF_FIELDS)
    {
      @Override
      public int get(int id)
      {
        switch(id) {
          case 0: return state_flags_.int_value();
          case 1: return max_progress_;
          case 2: return (int)progress_;
          default: return 0;
        }
      }
      @Override
      public void set(int id, int value)
      {
        switch(id) {
          case 0: state_flags_.value(value); return;
          case 1: max_progress_ = Math.max(value, 0); return;
          case 2: progress_ = MathHelper.clamp(value ,0, max_progress_); return;
          default: return;
        }
      }
    };

    // ITickableTileEntity ----------------------------------------------------------------------------------

    @Override
    public void tick()
    {
      // Tick timing
      if(world.isRemote() || (--tick_timer_ > 0)) return;
      tick_timer_ = TICK_INTERVAL;
      if(slow_timer_++ >= SLOW_INTERVAL) slow_timer_ = 0;
      // Tick timing
      final BlockState state = updateBlockstate();
      if((state == null) || (!(state.getBlock() instanceof RedAntHiveBlock))) return;
      if(!checkColony()) return;
      boolean dirty = false;
      dirty |= checkItemOutput();
      dirty |= checkWorkProcess();
      dirty |= checkItemInput();
      if(dirty) markDirty();
    }

    // TE specific ------------------------------------------------------------------------------------------

    boolean isSlowTimerTick()
    { return slow_timer_ == 0; }

    boolean isProcessing()
    { return (progress_ > 0) && (max_progress_ > 0); }

    @Nullable
    BlockState updateBlockstate()
    {
      BlockState state = getBlockState();
      state_flags_.powered(getWorld().isBlockPowered(getPos()));
      return state;
    }

    protected void setResultSlot(ItemStack stack)
    { main_inventory_.setInventorySlotContents(CMD_RESULT_START, stack); }

    protected ItemStack getResultSlot()
    { return main_inventory_.getStackInSlot(CMD_RESULT_START); }

    protected void setCommandSlot(ItemStack stack)
    { main_inventory_.setInventorySlotContents(CMD_STORAGE_START, stack); }

    protected ItemStack getCommandSlot()
    { return main_inventory_.getStackInSlot(CMD_STORAGE_START); }

    protected void setInputControlSlot(ItemStack stack)
    { main_inventory_.setInventorySlotContents(INP_STORAGE_START, stack); }

    protected ItemStack getInputControlSlot()
    { return main_inventory_.getStackInSlot(INP_STORAGE_START); }

    protected void setOutputControlSlot(ItemStack stack)
    { main_inventory_.setInventorySlotContents(OUT_STORAGE_START, stack); }

    protected ItemStack getOutputControlSlot()
    { return main_inventory_.getStackInSlot(OUT_STORAGE_START); }

    protected boolean insertionAllowed(int index, ItemStack stack)
    {
      if((!state_flags_.filteredinsert())) return true;
      if(left_filter_slot_range_.getStackInSlot(index).isEmpty()) return true;
      if(left_filter_slot_range_.getStackInSlot(index).getItem() == stack.getItem()) return true;
      return false;
    }

    protected ItemStack insertLeft(ItemStack stack)
    {
      if(!state_flags_.filteredinsert()) return left_storage_slot_range_.insert(stack);
      ItemStack remaining = stack.copy();
      for(int i=0; i<left_storage_slot_range_.getSizeInventory(); ++i) {
        if((!left_storage_slot_range_.getStackInSlot(i).isEmpty())
        || (left_filter_slot_range_.getStackInSlot(i).isEmpty())
        || (left_filter_slot_range_.getStackInSlot(i).getItem() == stack.getItem())
      ) {
          remaining = left_storage_slot_range_.insert(stack);
          if(remaining.isEmpty()) break;
        }
      }
      return remaining;
    }

    public void enableInsertionFilter(boolean enable)
    {
      state_flags_.filteredinsert(enable);
      if(!enable) {
        left_filter_slot_range_.clear();
      } else {
        for(int i=0; i<left_filter_slot_range_.size; ++i) {
          ItemStack stack = left_storage_slot_range_.getStackInSlot(i);
          if(stack.isEmpty()) {
            left_filter_slot_range_.setInventorySlotContents(i, ItemStack.EMPTY);
          } else {
            stack = stack.copy();
            stack.setCount(1);
            left_filter_slot_range_.setInventorySlotContents(i, stack);
          }
        }
      }
      markDirty();
    }

    private boolean checkColony()
    {
      final int max_ants = ant_storage_slot_range_.size * ant_storage_slot_range_.getInventoryStackLimit();
      ant_count_ = ant_storage_slot_range_.stream().filter(e->e.getItem()==ANTS_ITEM).mapToInt(ItemStack::getCount).sum();
      sugar_ticks_ = Math.max(sugar_ticks_ - TICK_INTERVAL, 0);
      can_use_sugar_ = (sugar_ticks_ <= 0) && ((ant_count_ < max_ants) || (isProcessing()));
      if(can_use_sugar_) {
        if(!left_storage_slot_range_.extract(new ItemStack(RED_SUGAR_ITEM,1)).isEmpty()) {
          sugar_ticks_ = 20 * sugar_boost_time_s;
        }
      }
      state_flags_.sugared(sugar_ticks_ > 0);
      colony_growth_progress_ += TICK_INTERVAL * (sugar_ticks_ > 0 ? 3 : 1);
      if(colony_growth_progress_ >= (20*hive_growth_latency_s)) {
        colony_growth_progress_ = 0;
        ant_storage_slot_range_.insert(new ItemStack(ANTS_ITEM));
      }
      if(ant_count_ == 0) {
        if(progress_ > 0) progress_ -= TICK_INTERVAL;
        return false;
      } else {
        ant_speed_ = (state_flags_.sugared() ? 2.0 : 1.0) * ((((double)ant_count_)/max_ants) * (1e-2 * normal_processing_speed_ant_count_percent));
        return true;
      }
    }

    private boolean checkItemOutput()
    {
      if(state_flags_.powered()) return false;
      if(getOutputControlSlot().isEmpty()) return false;
      final int outstack_size = 1 + (ant_count_/96);
      final Item control_item = getOutputControlSlot().getItem();
      final Direction output_facing = getBlockState().get(RedAntHiveBlock.FACING);
      final BlockPos output_position = getPos().offset(output_facing);
      if(control_item == Items.HOPPER) {
        if(Inventories.insertionPossible(getWorld(), output_position, output_facing.getOpposite(), true)) {
          for(ItemStack ostack:right_storage_slot_range_) {
            if(ostack.isEmpty()) continue;
            final ItemStack stack = ostack.copy();
            if(stack.getCount() > outstack_size) stack.setCount(outstack_size);
            final ItemStack remaining = Inventories.insert(getWorld(), output_position, output_facing.getOpposite(), stack, false, true);
            if(remaining.getCount() == stack.getCount()) continue;
            final int n_inserted = stack.getCount() - remaining.getCount();
            stack.setCount(n_inserted);
            right_storage_slot_range_.extract(stack);
            return true; // TE dirty.
          }
        } else {
          final BlockState trail_state = getWorld().getBlockState(output_position);
          if(!trail_state.isIn(ModContent.TRAIL_BLOCK)) return false;
          if((output_facing == trail_state.get(RedAntTrailBlock.HORIZONTAL_FACING)) || (output_facing==Direction.DOWN && trail_state.get(RedAntTrailBlock.UP))) return false;
          Inventories.dropStack(getWorld(), Vector3d.copyCentered(output_position).add(0,-.4,0), right_storage_slot_range_.extract(1), new Vector3d(0, -0.2, 0), 0.1, 0.1);
          return true;
        }
      } else if((control_item == Items.DROPPER) || (control_item == Items.DISPENSER)) {
        final BlockState state = getWorld().getBlockState(output_position);
        if(state.hasOpaqueCollisionShape(getWorld(), output_position)) return false;
        ItemStack stack = right_storage_slot_range_.extract(1, true);
        Vector3d drop_pos = Vector3d.copyCentered(output_position).add(Vector3d.copy(output_facing.getOpposite().getDirectionVec()).scale(0.3));
        if(control_item == Items.DISPENSER) {
          Vector3d speed = Vector3d.copy(output_facing.getDirectionVec()).scale(0.6);
          Inventories.dropStack(getWorld(), drop_pos, stack, speed, 0.1, 0.2);
        } else {
          Vector3d speed = Vector3d.copy(output_facing.getDirectionVec()).scale(0.05);
          Inventories.dropStack(getWorld(), drop_pos, stack, speed, 0.1,0.1);
        }
      }
      return false;
    }

    private boolean checkItemInput()
    {
      if(getInputControlSlot().getItem() != Items.HOPPER) return false;
      final int instack_size = 1 + (ant_count_/96);
      boolean only_existing = false; // can be used later as lock
      boolean dirty = false;
      final Direction input_facing = getBlockState().get(RedAntHiveBlock.FACING).getOpposite();
      // Item handler
      {
        final IItemHandler ih = Inventories.itemhandler(getWorld(), getPos().offset(input_facing), input_facing.getOpposite(), true);
        if(ih!=null) {
          for(ItemStack ref_stack:left_storage_slot_range_) {
            if((ref_stack.getCount() >= ref_stack.getMaxStackSize()) || (ref_stack.getCount() >= (left_storage_slot_range_.getInventoryStackLimit()))) continue;
            final ItemStack fetched = Inventories.extract(ih, only_existing ? ref_stack : null, instack_size, false);
            if(!fetched.isEmpty()) {
              insertLeft(fetched);
              return true;
            }
          }
        }
      }
      // Item entities
      {
        final Vector3d aabb_extension = Vector3d.copy(input_facing.getDirectionVec()).scale(0.5);
        final AxisAlignedBB aabb = (new AxisAlignedBB(getPos().offset(input_facing))).expand(aabb_extension);
        final List<ItemEntity> items = getWorld().getEntitiesWithinAABB(ItemEntity.class, aabb, (e)->e.isAlive() && (!e.getItem().isEmpty()));
        for(ItemEntity ie:items) {
          final ItemStack stack = ie.getItem().copy();
          final ItemStack remaining = insertLeft(stack);
          if(remaining.getCount() == stack.getCount()) continue;
          dirty = true;
          if(remaining.isEmpty()) {
            ie.remove();
          } else {
            ie.setItem(remaining);
          }
        }
      }
      return dirty;
    }

    private boolean checkWorkProcess()
    {
      Object cat = processing_command_item_mapping.getOrDefault(getCommandSlot().getItem(), null);
      if(cat == null) {
        // No command item inserted
        max_progress_ = 0;
        progress_ = 0;
        last_recipe_ = "";
        state_flags_.mask(StateFlags.mask_nofuel|StateFlags.mask_norecipe|StateFlags.mask_noingr, 0);
        cache_slot_range_.move(left_storage_slot_range_);
        if(!getResultSlot().isEmpty()) {
          setResultSlot(ItemStack.EMPTY);
          return true; // rslot changed
        } else {
          return false; // no changes
        }
      } else if(progress_ < 0) {
        // re-check delay, currently nothing processible, don't waste CPU.
        progress_ = Math.min(0, progress_ + TICK_INTERVAL);
        if(progress_ < 0) return itemPassThrough(cat);
        // Flush cache when the delay just expired.
        cache_slot_range_.move(right_storage_slot_range_);
        return true;
      } else if((max_progress_ > 0) && (progress_ < max_progress_)) {
        // processing, don't waste performance until it is done.
        progress_ = Math.min(max_progress_, progress_ + Math.max(TICK_INTERVAL, (int)((1.0+ant_speed_) * TICK_INTERVAL)));
        itemPassThrough(cat);
      } else {
        // processing by recipe type
        boolean is_done = (progress_ >= max_progress_) && (max_progress_ > 0);
        state_flags_.mask(StateFlags.mask_nofuel|StateFlags.mask_norecipe|StateFlags.mask_noingr, 0);
        max_progress_ = 0;
        progress_ = 0;
        if(cat instanceof IRecipeType) {
          if(cat == IRecipeType.CRAFTING) {
            processCrafting((IRecipeType)cat, is_done);
          } else if((cat == IRecipeType.SMELTING) || (cat == IRecipeType.BLASTING) || (cat == IRecipeType.SMOKING)) {
            processFurnace((IRecipeType)cat, is_done);
          }
        } else if(cat == Items.HOPPER) {
          return processHopper();
        } else if(cat == Items.COMPOSTER) {
          return processComposter();
        }
      }
      return true;
    }

    private boolean itemPassThrough(Object type)
    {
      if(state_flags_.nopassthrough()) return false;
      if(!isSlowTimerTick()) return false;
      if(type == IRecipeType.CRAFTING) {
        return itemPassThroughCrafting((IRecipeType)type);
      } else if((type == IRecipeType.SMELTING) || (type == IRecipeType.BLASTING) || (type == IRecipeType.SMOKING)) {
        return itemPassThroughFurnace((IRecipeType)type);
      } else if((type == Items.COMPOSTER)) {
        return itemPassThroughComposter();
      } else {
        return false;
      }
    }

    private boolean processCrafting(IRecipeType<?> recipe_type, boolean is_done)
    {
      progress_ = -40; // Default "cannot process", needs waiting time.
      if(is_done) {
        setResultSlot(ItemStack.EMPTY);
        List<ICraftingRecipe> crafting_recipes;
        if(!last_recipe_.isEmpty()) {
          crafting_recipes = Crafting.getCraftingRecipe(getWorld(), new ResourceLocation(last_recipe_)).map(Collections::singletonList).orElse(Collections.emptyList());
        } else {
          crafting_recipes = Crafting.get3x3CraftingRecipes(getWorld(), cache_slot_range_);
        }
        if(!crafting_recipes.isEmpty()) {
          final List<ItemStack> remaining = Crafting.get3x3RemainingItems(getWorld(), cache_slot_range_, crafting_recipes.get(0));
          final ItemStack result = Crafting.get3x3CraftingResult(getWorld(), cache_slot_range_, crafting_recipes.get(0));
          cache_slot_range_.clear();
          cache_slot_range_.insert(result);
          for(ItemStack stack:remaining) {
            if(!stack.isEmpty()) {
              cache_slot_range_.insert(stack);
            }
          }
          if(cache_slot_range_.move(right_storage_slot_range_)) {
            progress_ = 0;
            return true;
          }
        } else {
          // cache will be transferred anyway next cycle.
        }
        progress_ = -TICK_INTERVAL;
        return true;
      } else {
        if(!cache_slot_range_.isEmpty()) return false;
        // Initiate new crafting process
        List<ICraftingRecipe> crafting_recipes = Crafting.get3x3CraftingRecipes(getWorld(), grid_storage_slot_range_);
        if(!crafting_recipes.isEmpty() && !last_recipe_.isEmpty()) {
          List<ICraftingRecipe> last = Crafting.getCraftingRecipe(getWorld(), new ResourceLocation(last_recipe_)).map(Collections::singletonList).orElse(Collections.emptyList());
          if(!last.isEmpty() && crafting_recipes.contains(last.get(0))) {
            crafting_recipes = last;
          }
        }
        last_recipe_ = "";
        if(crafting_recipes.isEmpty()) {
          state_flags_.norecipe(true);
          return false;
        }
        ICraftingRecipe selected_recipe = null;
        List<ItemStack> selected_placement = Collections.emptyList();
        for(ICraftingRecipe recipe:crafting_recipes) {
          final List<ItemStack> placement = Crafting.get3x3Placement(getWorld(), recipe, left_storage_slot_range_, grid_storage_slot_range_);
          if(placement.isEmpty()) continue;
          selected_recipe = recipe;
          selected_placement = placement;
          break;
        }
        if(selected_placement.isEmpty()) {
          state_flags_.noingr(true);
          return false;
        }
        cache_slot_range_.clear();
        for(int i=0; i<selected_placement.size(); ++i) {
          cache_slot_range_.setInventorySlotContents(i, left_storage_slot_range_.extract(selected_placement.get(i)));
        }
        setResultSlot(Crafting.get3x3CraftingResult(getWorld(), cache_slot_range_, selected_recipe));
        last_recipe_ = selected_recipe.getId().toString();
        progress_ = 0;
        max_progress_ = 60 + (4*cache_slot_range_.stream().mapToInt(ItemStack::getCount).sum());
        return true;
      }
    }

    private boolean itemPassThroughCrafting(IRecipeType<?> recipe_type)
    {
      if(last_recipe_.isEmpty()) return false;
      final ICraftingRecipe recipe = Crafting.getCraftingRecipe(getWorld(), new ResourceLocation(last_recipe_)).orElse(null);
      if(recipe==null) return false;
      final List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ing->(ing!=Ingredient.EMPTY)).collect(Collectors.toList());
      if(ingredients.isEmpty()) return false;
      for(int i=0; i<left_storage_slot_range_.size; ++i) {
        ItemStack stack = left_storage_slot_range_.getStackInSlot(i);
        if(ingredients.stream().noneMatch(ing->ing.test(stack))) {
          if(left_storage_slot_range_.move(i,right_storage_slot_range_)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean processFurnace(IRecipeType<?> recipe_type, boolean is_done)
    {
      last_recipe_ = "";
      progress_ = -40; // Default "cannot process or transfer", needs waiting time.
      if(is_done) {
        if(!getResultSlot().isEmpty()) {
          cache_slot_range_.setInventorySlotContents(0, getResultSlot());
          setResultSlot(ItemStack.EMPTY);
        }
        cache_slot_range_.move(right_storage_slot_range_);
        if(cache_slot_range_.isEmpty()) progress_ = 0;
        state_flags_.nofuel(false);
        state_flags_.noingr(false);
        return true;
      } else {
        final Inventories.InventoryRange input_slots = left_storage_slot_range_;
        final List<Item> allowed_fuel = grid_storage_slot_range_.stream().filter(e->!e.isEmpty()).map(ItemStack::getItem).collect(Collectors.toList());
        Tuple<Integer,AbstractCookingRecipe> recipe_search;
        // Determine recipe or abort
        {
          recipe_search = input_slots.find((slot,stack)->{
            // Recipes for items that are not fuel.
            if(Crafting.isFuel(world, stack)) return Optional.empty();
            Optional<AbstractCookingRecipe> recipe = Crafting.getFurnaceRecipe(recipe_type, world, stack);
            return recipe.map(r->new Tuple<>(slot, r));
          }).orElse(null);
          if(recipe_search == null) {
            // Recipes for items that are also fuel, but not in the fuel slot list.
            recipe_search = input_slots.find((slot,stack)->{
              if((!allowed_fuel.isEmpty()) && (!allowed_fuel.contains(stack.getItem()))) return Optional.empty();
              Optional<AbstractCookingRecipe> recipe = Crafting.getFurnaceRecipe(recipe_type, world, stack);
              return recipe.map(r->new Tuple<>(slot, r));
            }).orElse(null);
          }
          if(recipe_search == null) {
            // No recipe, TE is not dirty
            state_flags_.noingr(true);
            return false;
          }
        }
        final AbstractCookingRecipe recipe = recipe_search.getB();
        final int smelting_input_slot = recipe_search.getA();
        final int fuel_time_needed = (int)Math.ceil((1.0+ant_speed_) * recipe.getCookTime());
        // Collect fuel
        {
          final int initial_fuel_left = fuel_left_;
          final boolean enough_fuel = input_slots.iterate((slot,stack)->{
            if(fuel_left_ >= fuel_time_needed) return true;
            if((slot == smelting_input_slot) || (stack.isEmpty())) return false;
            int t = Crafting.getFuelBurntime(world, stack);
            if(t <= 0) return false;
            if((!allowed_fuel.isEmpty()) && (!allowed_fuel.contains(stack.getItem()))) return false;
            while((stack.getCount() > 0) && (fuel_left_ < fuel_time_needed)) {
              stack.shrink(1);
              fuel_left_ += t;
            }
            if(stack.isEmpty()) stack = ItemStack.EMPTY;
            input_slots.setInventorySlotContents(slot, stack);
            return false;
          });
          if(!enough_fuel) {
            // Fuel insufficient, TE dirty if fuel_left_ changed.
            state_flags_.nofuel(true);
            return (fuel_left_ != initial_fuel_left);
          }
        }
        // Start processing.
        {
          if(fuel_left_ < fuel_time_needed) return true;
          state_flags_.nofuel(false);
          state_flags_.norecipe(false);
          setResultSlot(recipe.getRecipeOutput());
          fuel_left_ -= fuel_time_needed;
          max_progress_ = recipe.getCookTime();
          last_recipe_ = recipe.getId().toString();
          progress_ = 0;
          cache_slot_range_.setInventorySlotContents(0, input_slots.getStackInSlot(smelting_input_slot).split(1));
          return true;
        }
      }
    }

    private boolean itemPassThroughFurnace(IRecipeType<?> recipe_type)
    {
      final List<Item> allowed_fuel = grid_storage_slot_range_.stream().filter(e->!e.isEmpty()).map(ItemStack::getItem).collect(Collectors.toList());
      final List<Integer> unmatching_slots = left_storage_slot_range_.collect((slot,stack)->{
        // Recipes for items that are not fuel.
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM)) return Optional.empty();
        if(Crafting.isFuel(world, stack) && (allowed_fuel.isEmpty() || allowed_fuel.contains(stack.getItem()))) return Optional.empty();
        if(Crafting.getFurnaceRecipe(recipe_type, world, stack).isPresent()) return Optional.empty();
        return Optional.of(slot);
      });
      // Randomly pick a slot among the eligable and try to move that to the output side.
      if(unmatching_slots.isEmpty()) return false;
      final int picked_slot = unmatching_slots.get(world.getRandom().nextInt(unmatching_slots.size()));
      return left_storage_slot_range_.move(picked_slot, right_storage_slot_range_);
    }

    private boolean processComposter()
    {
      if(cache_slot_range_.isEmpty()) {
        final ItemStack bonemeal = new ItemStack(Items.BONE_MEAL);
        int processed = 0;
        for(ItemStack stack: left_storage_slot_range_) {
          if(stack.isEmpty()) continue;
          double chance = Crafting.getCompostingChance(stack);
          if(chance <= 0) continue;
          ++processed;
          stack.shrink(1);
          if(((getWorld().getRandom().nextDouble() * 7) > chance)) continue;
          if(!right_storage_slot_range_.insert(bonemeal).isEmpty()) cache_slot_range_.insert(bonemeal);
        }
        if(processed > 0) {
          progress_ = 0;
          max_progress_ = 20 + (processed*10);
          setResultSlot(bonemeal);
          return true;
        }
      }
      setResultSlot(ItemStack.EMPTY);
      progress_ = -40;
      return false;
    }

    private boolean itemPassThroughComposter()
    {
      if(!cache_slot_range_.isEmpty()) return false;
      for(int i=0; i<left_storage_slot_range_.size; ++i) {
        final ItemStack stack = left_storage_slot_range_.getStackInSlot(i);
        if(stack.isEmpty() || (Crafting.getCompostingChance(stack)>0)) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) return true;
      }
      return false;
    }

    private boolean processHopper()
    {
      progress_ = 0;
      max_progress_ = TICK_INTERVAL;
      for(int i=0; i<left_storage_slot_range_.size; ++i) {
        ItemStack stack = left_storage_slot_range_.getStackInSlot(i);
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM)) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) return true;
      }
      return false;
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Container
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveContainer extends Container implements Networking.INetworkSynchronisableContainer
  {
    private final PlayerEntity player_;
    private final IWorldPosCallable wpc_;
    private final IIntArray fields_;
    private final Inventories.InventoryRange inventory_;
    private final Inventories.InventoryRange left_storage_slot_range_;
    private final Inventories.InventoryRange left_filter_slot_range_;
    private final Inventories.InventoryRange right_storage_slot_range_;
    private final Inventories.InventoryRange player_inventory_range_;
    private final Inventories.InventoryRange grid_storage_slot_range_;
    private final Inventories.InventoryRange ant_slot_range_;
    public  final StorageSlot command_slot;
    public  final StorageSlot input_selection_slot;
    public  final StorageSlot output_selection_slot;
    public  final Containers.LockedSlot result_slot;
    public  final List<StorageSlot> grid_slots;

    public final int field(int index)
    { return fields_.get(index); }

    public RedAntHiveContainer(int cid, PlayerInventory player_inventory)
    { this(cid, player_inventory, new Inventory(RedAntHiveTileEntity.NUM_MAIN_INVENTORY_SLOTS), IWorldPosCallable.DUMMY, new IntArray(RedAntHiveTileEntity.NUM_OF_FIELDS)); }

    private RedAntHiveContainer(int cid, PlayerInventory player_inventory, IInventory block_inventory, IWorldPosCallable wpc, IIntArray fields)
    {
      super(ModContent.CT_HIVE, cid);
      wpc_ = wpc;
      player_ = player_inventory.player;
      fields_ = fields;
      player_inventory_range_ = Inventories.InventoryRange.fromPlayerInventory(player_);
      inventory_ = new Inventories.InventoryRange(block_inventory, 0, block_inventory.getSizeInventory());
      inventory_.setValidator(RedAntHiveTileEntity.main_inventory_validator());
      left_storage_slot_range_  =  new Inventories.InventoryRange(inventory_, RedAntHiveTileEntity.LEFT_STORAGE_START , RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS);
      left_filter_slot_range_   =  new Inventories.InventoryRange(inventory_, RedAntHiveTileEntity.LEFT_FILTER_START , RedAntHiveTileEntity.LEFT_FILTER_NUM_SLOTS, RedAntHiveTileEntity.LEFT_FILTER_NUM_ROWS);
      right_storage_slot_range_ =  new Inventories.InventoryRange(inventory_, RedAntHiveTileEntity.RIGHT_STORAGE_START, RedAntHiveTileEntity.RIGHT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS);
      grid_storage_slot_range_  =  new Inventories.InventoryRange(inventory_, RedAntHiveTileEntity.GRID_STORAGE_START, RedAntHiveTileEntity.GRID_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS);
      ant_slot_range_ =  new Inventories.InventoryRange(inventory_, RedAntHiveTileEntity.ANT_STORAGE_START, RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS);
      wpc_.consume((w,p)->inventory_.openInventory(player_));
      int i = -1;
      // left storage slots
      for(int y = 0; y<RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 8+x*18, ypos = 36+y*18;
          addSlot(new StorageSlot(inventory_, ++i, xpos, ypos));
        }
      }
      // right storage slots
      for(int y = 0; y<RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.RIGHT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 134+x*18, ypos = 18+y*18;
          addSlot(new StorageSlot(inventory_, ++i, xpos, ypos));
        }
      }
      // ant storage slots
      for(int y = 0; y<RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 62+x*18, ypos = 18+y*18;
          addSlot(new StorageSlot(inventory_, ++i, xpos, ypos));
        }
      }
      // cmd storage slot
      {
        int xpos = 70, ypos = 50;
        addSlot(command_slot = (new StorageSlot(inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // grid storage slots
      {
        List<StorageSlot> slots = new ArrayList<>();
        for(int y = 0; y<RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS; ++y) {
          for(int x = 0; x<(RedAntHiveTileEntity.GRID_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS); ++x) {
            int xpos = 62+x*18, ypos = 72+y*18;
            StorageSlot slot = (new StorageSlot(inventory_, ++i, xpos, ypos)).setSlotStackLimit(1);
            slots.add(slot);
            addSlot(slot);
          }
        }
        grid_slots = ImmutableList.copyOf(slots);
      }
      // input selection storage slot
      {
        int xpos = 26, ypos = 18;
        addSlot(input_selection_slot=(new StorageSlot(inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // output selection storage slot
      {
        int xpos = 134, ypos = 108;
        addSlot(output_selection_slot=(new StorageSlot(inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // result storage slot
      {
        int xpos = 92, ypos = 50;
        addSlot(result_slot = new Containers.LockedSlot(inventory_, ++i, xpos, ypos));
        result_slot.enabled = false;
      }
      // block slot fillup for stack synchronisation
      {
        while(i<inventory_.getSizeInventory()) addSlot(new Containers.HiddenSlot(inventory_, ++i));
      }
      // player slots hotbar
      for(int x=0; x<9; ++x) {
        addSlot(new Slot(player_inventory, x, 8+x*18, 198));
      }
      // player slots storage
      for(int y=0; y<3; ++y) {
        for(int x=0; x<9; ++x) {
          addSlot(new Slot(player_inventory, x+y*9+9, 8+x*18, 140+y*18));
        }
      }
      this.trackIntArray(fields_);
    }

    @Override
    public boolean canInteractWith(PlayerEntity player)
    { return inventory_.isUsableByPlayer(player); }

    @Override
    public void onContainerClosed(PlayerEntity player)
    {
      super.onContainerClosed(player);
      inventory_.closeInventory(player);
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity player, int index)
    {
      final int player_start_index = inventory_.getSizeInventory();
      Slot slot = getSlot(index);
      if((slot==null) || (!slot.getHasStack())) return ItemStack.EMPTY;
      ItemStack slot_stack = slot.getStack();
      ItemStack transferred = slot_stack.copy();
      if((index>=0) && (index<player_start_index)) {
        // Storage slots -> to player
        if(!mergeItemStack(slot_stack, player_start_index, player_start_index+36, false)) return ItemStack.EMPTY;
      } else if((index >= player_start_index) && (index <= player_start_index+36)) {
        // Player slot -> to storage
        if(slot_stack.getItem() == ANTS_ITEM) {
          if(!mergeItemStack(slot_stack, RedAntHiveTileEntity.ANT_STORAGE_START, RedAntHiveTileEntity.ANT_STORAGE_START+RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS, false)) {
            return ItemStack.EMPTY;
          }
        } else {
          ItemStack remaining = left_storage_slot_range_.insert(slot_stack);
          if(!remaining.isEmpty()) remaining = right_storage_slot_range_.insert(remaining.copy());
          if(remaining.getCount() == slot_stack.getCount()) return ItemStack.EMPTY;
          slot_stack.setCount(remaining.getCount());
        }
      } else {
        // Invalid slot
        return ItemStack.EMPTY;
      }
      if(slot_stack.isEmpty()) {
        slot.putStack(ItemStack.EMPTY);
      } else {
        slot.onSlotChanged();
      }
      if(slot_stack.getCount() == transferred.getCount()) return ItemStack.EMPTY;
      slot.onTake(player, slot_stack);
      return transferred;
    }

    // INetworkSynchronisableContainer ---------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(CompoundNBT nbt)
    { Networking.PacketContainerSyncClientToServer.sendToServer(windowId, nbt); }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String key, int value)
    {
      CompoundNBT nbt = new CompoundNBT();
      nbt.putInt(key, value);
      Networking.PacketContainerSyncClientToServer.sendToServer(windowId, nbt);
    }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message)
    {
      CompoundNBT nbt = new CompoundNBT();
      nbt.putString("action", message);
      Networking.PacketContainerSyncClientToServer.sendToServer(windowId, nbt);
    }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message, CompoundNBT nbt)
    {
      nbt.putString("action", message);
      Networking.PacketContainerSyncClientToServer.sendToServer(windowId, nbt);
    }

    @Override
    public void onServerPacketReceived(int windowId, CompoundNBT nbt)
    {}

    @Override
    public void onClientPacketReceived(int windowId, PlayerEntity player, CompoundNBT nbt)
    {
      if(!nbt.contains("action")) return;
      boolean changed = false;
      final int slotId = nbt.contains("slot") ? nbt.getInt("slot") : -1;
      final RedAntHiveTileEntity hive = ((RedAntHiveTileEntity)((Inventories.StorageInventory)inventory_.inventory).getTileEntity());
      switch(nbt.getString("action")) {
        case "input-filter-on":  { hive.enableInsertionFilter(true);  changed = true; break; }
        case "input-filter-off": { hive.enableInsertionFilter(false); changed = true; break; }
        case "pass-through-on":  { hive.state_flags_.nopassthrough(false); changed = true; break; }
        case "pass-through-off": { hive.state_flags_.nopassthrough(true); changed = true; break; }
        default: break;
      }
      if(changed) {
        inventory_.markDirty();
        player.inventory.markDirty();
        detectAndSendChanges();
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // GUI
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class RedAntHiveGui extends ContainerGui<RedAntHiveContainer>
  {
    protected final Guis.BackgroundImage gui_background_;
    protected final Guis.BackgroundImage grid_background_;
    protected final Guis.BackgroundImage result_background_;
    protected final Guis.BackgroundImage powered_indicator_;
    protected final Guis.BackgroundImage sugar_indicator_;
    protected final Guis.BackgroundImage norecipe_indicator_;
    protected final Guis.BackgroundImage noingredients_indicator_;
    protected final Guis.BackgroundImage nofuel_indicator_;
    protected final Guis.HorizontalProgressBar progress_bar_;
    protected final Guis.CheckBox left_filter_enable_;
    protected final Guis.CheckBox passthrough_enable_;
    protected final TooltipDisplay tooltip_ = new TooltipDisplay();
    protected final PlayerEntity player_;
    protected final RedAntHiveTileEntity.StateFlags state_flags_ = new RedAntHiveTileEntity.StateFlags(0);
    protected final ITextComponent EMPTY_TOOLTIP = new StringTextComponent("");

    public RedAntHiveGui(RedAntHiveContainer container, PlayerInventory player_inventory, ITextComponent title)
    {
      super(container, player_inventory, title, new ResourceLocation(ModAnthillInside.MODID, "textures/gui/hive_gui.png"));
      player_ = player_inventory.player;
      xSize = 176;
      ySize = 222;
      gui_background_    = new Guis.BackgroundImage(background_image, xSize,ySize, new Coord2d(0,0));
      grid_background_   = new Guis.BackgroundImage(background_image, 54,54, new Coord2d(180,71));
      result_background_ = new Guis.BackgroundImage(background_image, 22,18, new Coord2d(206,49));
      progress_bar_      = new HorizontalProgressBar(background_image, 40,8, new Coord2d(180,0), new Coord2d(180,8));
      powered_indicator_ = new Guis.BackgroundImage(background_image, 9,8, new Coord2d(181,35));
      sugar_indicator_   = new Guis.BackgroundImage(background_image, 12,12, new Coord2d(230,19));
      norecipe_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(196,17));
      noingredients_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(212,17));
      nofuel_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(180,17));
      left_filter_enable_ = (new Guis.CheckBox(background_image, 5,6, new Coord2d(182,46), new Coord2d(182,53))).onclick((box)->{
        container.onGuiAction(box.checked() ? "input-filter-on" : "input-filter-off");
      });
      passthrough_enable_ = (new Guis.CheckBox(background_image, 11,6, new Coord2d(189,46), new Coord2d(189,53))).onclick((box)->{
        container.onGuiAction(box.checked() ? "pass-through-on" : "pass-through-off");
      });
    }

    @Override
    public void init()
    {
      super.init();
      gui_background_.init(this, new Coord2d(0,0)).show();
      grid_background_.init(this, new Coord2d(61,71)).hide();
      result_background_.init(this, new Coord2d(87,49)).hide();
      powered_indicator_.init(this, new Coord2d(153,6)).hide();
      sugar_indicator_.init(this, new Coord2d(47,20)).hide();
      norecipe_indicator_.init(this, new Coord2d(92,50)).hide();
      noingredients_indicator_.init(this, new Coord2d(92,50)).hide();
      nofuel_indicator_.init(this, new Coord2d(92,50)).hide();
      addButton(progress_bar_.init(this, new Coord2d(69, 38)));
      addButton(left_filter_enable_.init(this, new Coord2d(20, 126)));
      addButton(passthrough_enable_.init(this, new Coord2d(28, 126)));
      left_filter_enable_.checked(state_flags_.filteredinsert());
      passthrough_enable_.checked(!state_flags_.nopassthrough());

      final String prefix = ModContent.HIVE_BLOCK.getTranslationKey() + ".tooltips.";
      final int x0 = getGuiLeft(), y0 = getGuiTop();
      tooltip_.init(
        new TooltipDisplay.TipRange(
          x0+164,y0+6, 7,9,
          ()->(new TranslationTextComponent(prefix + "help"))
        ),
        new TooltipDisplay.TipRange(
          powered_indicator_.x, powered_indicator_.y, powered_indicator_.getWidth(), powered_indicator_.getHeight(),
          ()->(powered_indicator_.visible ? new TranslationTextComponent(prefix + "powered") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          sugar_indicator_.x, sugar_indicator_.y, sugar_indicator_.getWidth(), sugar_indicator_.getHeight(),
          ()->(sugar_indicator_.visible ? new TranslationTextComponent(prefix + "sugartrip") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          norecipe_indicator_.x, norecipe_indicator_.y, norecipe_indicator_.getWidth(), norecipe_indicator_.getHeight(),
          ()->(norecipe_indicator_.visible ? new TranslationTextComponent(prefix + "norecipe") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          noingredients_indicator_.x, noingredients_indicator_.y, noingredients_indicator_.getWidth(), noingredients_indicator_.getHeight(),
          ()->(noingredients_indicator_.visible ? new TranslationTextComponent(prefix + "noingredients") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          nofuel_indicator_.x, nofuel_indicator_.y, nofuel_indicator_.getWidth(), nofuel_indicator_.getHeight(),
          ()->(nofuel_indicator_.visible ? new TranslationTextComponent(prefix + "nofuel") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          x0+12, y0+22, 8,8,
          ()->(new TranslationTextComponent(prefix + "inputside"))
        ),
        new TooltipDisplay.TipRange(
          x0+156, y0+112, 8,8,
          ()->(new TranslationTextComponent(prefix + "outputside"))
        ),
        new TooltipDisplay.TipRange(
          x0+28, y0+126, 11,6,
          ()->(new TranslationTextComponent(prefix + "passthroughlock"))
        ),
        new TooltipDisplay.TipRange(
          x0+20, y0+126, 5,6,
          ()->(new TranslationTextComponent(prefix + "insertionlock"))
        ),
        new TooltipDisplay.TipRange(
          x0+26, y0+18, 16,16,
          ()->(getContainer().input_selection_slot.getHasStack() ? EMPTY_TOOLTIP : new TranslationTextComponent(prefix + "inputselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+134, y0+108, 16,16,
          ()->(getContainer().output_selection_slot.getHasStack() ? EMPTY_TOOLTIP : new TranslationTextComponent(prefix + "outputselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+70, y0+50, 16,16,
          ()->(getContainer().command_slot.getHasStack() ? EMPTY_TOOLTIP : new TranslationTextComponent(prefix + "workselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+62, y0+18, 52,16,
          ()->(getContainer().ant_slot_range_.isEmpty() ? new TranslationTextComponent(prefix + "antslots") : EMPTY_TOOLTIP )
        )
      ).delay(400);
    }

    public void tick()
    {
      super.tick();
      final RedAntHiveContainer container = (RedAntHiveContainer)getContainer();
      state_flags_.value(container.field(0));
      final ItemStack cmdstack = container.command_slot.getStack();
      final boolean show_process = !cmdstack.isEmpty() && (cmdstack.getItem()!=Items.HOPPER);
      grid_background_.visible = (show_process && (cmdstack.getItem()!=Items.COMPOSTER)) || (!container.grid_storage_slot_range_.isEmpty());
      container.grid_slots.forEach(slot->{slot.enabled=grid_background_.visible;});
      progress_bar_.visible = show_process;
      progress_bar_.active = progress_bar_.visible;
      result_background_.visible = progress_bar_.visible;
      if(progress_bar_.visible) progress_bar_.setMaxProgress(container.field(1)).setProgress(container.field(2));
      powered_indicator_.visible = state_flags_.powered();
      sugar_indicator_.visible = state_flags_.sugared();
      norecipe_indicator_.visible = state_flags_.norecipe();
      noingredients_indicator_.visible = state_flags_.noingr();
      nofuel_indicator_.visible = state_flags_.nofuel();
      left_filter_enable_.checked(state_flags_.filteredinsert());
      passthrough_enable_.checked(!state_flags_.nopassthrough());
    }

    @Override
    public void render(MatrixStack mx, int mouseX, int mouseY, float partialTicks)
    {
      renderBackground(mx);
      super.render(mx, mouseX, mouseY, partialTicks);
      if(!tooltip_.render(mx, this, mouseX, mouseY)) renderHoveredTooltip(mx, mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(MatrixStack mx, int x, int y)
    {
      font.func_243248_b(mx, title, (float)titleX+1, (float)titleY+1, 0x303030);
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, ClickType type)
    {
      tooltip_.resetTimer();
      super.handleMouseClick(slot, slotId, button, type);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
    {
      tooltip_.resetTimer();
      return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void drawGuiContainerBackgroundLayer(MatrixStack mx, float partialTicks, int mouseX, int mouseY)
    {
      RenderSystem.enableBlend();
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      getMinecraft().getTextureManager().bindTexture(background_image);
      final RedAntHiveContainer container = (RedAntHiveContainer)getContainer();
      final int x0=getGuiLeft(), y0=getGuiTop(), w=getXSize(), h=getYSize();
      // backgrounds images
      {
        gui_background_.draw(mx, this);
        grid_background_.draw(mx,this);
        result_background_.draw(mx,this);
        powered_indicator_.draw(mx,this);
        sugar_indicator_.draw(mx,this);
        norecipe_indicator_.draw(mx,this);
        noingredients_indicator_.draw(mx,this);
        nofuel_indicator_.draw(mx,this);
      }
      // result slot
      {
        final ItemStack stack = container.result_slot.getStack();
        if(!stack.isEmpty()) renderItemTemplate(mx, stack, container.result_slot.xPos , container.result_slot.yPos);
      }
      // ants background
      {
        for(int i=0; i<RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS; ++i) {
          final Slot slot = container.getSlot(RedAntHiveTileEntity.ANT_STORAGE_START+i);
          if(slot.getStack().isEmpty()) {
            renderItemTemplate(mx, new ItemStack(ANTS_ITEM), slot.xPos , slot.yPos);
          }
        }
      }
      // left filter slot background
      {
        if(state_flags_.filteredinsert()) {
          for(int i=0; i<RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS; ++i) {
            final Slot slot = container.getSlot(RedAntHiveTileEntity.LEFT_STORAGE_START+i);
            if(slot.getStack().isEmpty()) {
              final ItemStack filter_stack = container.left_filter_slot_range_.getStackInSlot(i);
              if(!filter_stack.isEmpty()) {
                renderItemTemplate(mx, filter_stack, slot.xPos , slot.yPos);
              }
            }
          }
        }
      }
      RenderSystem.disableBlend();
    }
  }

}
