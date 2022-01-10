/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.blocks;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import wile.anthillinside.ModAnthillInside;
import wile.anthillinside.ModConfig;
import wile.anthillinside.ModContent;
import wile.anthillinside.blocks.RedAntTrail.RedAntTrailBlock;
import wile.anthillinside.libmc.blocks.StandardBlocks;
import wile.anthillinside.libmc.blocks.StandardEntityBlocks;
import wile.anthillinside.libmc.detail.*;
import wile.anthillinside.libmc.ui.Containers;
import wile.anthillinside.libmc.ui.Containers.StorageSlot;
import wile.anthillinside.libmc.ui.Guis;
import wile.anthillinside.libmc.ui.Guis.ContainerGui;
import wile.anthillinside.libmc.ui.Guis.Coord2d;
import wile.anthillinside.libmc.ui.Guis.HorizontalProgressBar;
import wile.anthillinside.libmc.util.IntegralBitSet;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;


public class RedAntHive
{
  private static final Item ANTS_ITEM = ModContent.ANTS_ITEM;
  private static final Item RED_SUGAR_ITEM = ModContent.RED_SUGAR_ITEM;
  private static int hive_drop_probability_percent = 3;
  private static int sugar_boost_time_s = 5;
  private static int hive_growth_latency_s = 120;
  private static int normal_processing_speed_ant_count_percent = 100;
  private static int animal_feeding_speed_percent = 100;
  private static int animal_feeding_entity_limit = 16;
  private static int animal_feeding_xz_radius = 3;
  private static int farming_speed_percent = 100;
  private static final int brewing_fuel_efficiency_percent = 75;
  private static final HashMap<Item, Object> processing_command_item_mapping = new HashMap<>();

  private static class ProcessingHandler
  {
    public final Item item;
    public final BiFunction<RedAntHiveTileEntity, Boolean, Boolean> handler;
    public final Function<RedAntHiveTileEntity, Boolean> passthrough_handler;
    public ProcessingHandler(Item item, BiFunction<RedAntHiveTileEntity, Boolean, Boolean> handler, Function<RedAntHiveTileEntity, Boolean> passthrough_handler)
    { this.item = item; this.handler = handler; this.passthrough_handler = passthrough_handler; }
  }

  public static void on_config(int ore_minining_spawn_probability_percent, int ant_speed_scaler_percent, int sugar_time_s,
                               int growth_latency_s, int feeding_speed_factor_percent, int feeding_entity_limit, int feeding_xz_radius,
                               int farming_speed_factor_percent
  ){
    hive_drop_probability_percent = Mth.clamp(ore_minining_spawn_probability_percent, 1, 99);
    normal_processing_speed_ant_count_percent = Mth.clamp(ant_speed_scaler_percent, 10, 190);
    sugar_boost_time_s = Mth.clamp(sugar_time_s, 1, 60);
    hive_growth_latency_s = Mth.clamp(growth_latency_s, 10, 600);
    animal_feeding_speed_percent = Mth.clamp(feeding_speed_factor_percent, 0, 200);
    animal_feeding_entity_limit = Mth.clamp(feeding_entity_limit, 3, 64);
    animal_feeding_xz_radius = Mth.clamp(feeding_xz_radius,1, 5);
    farming_speed_percent = (farming_speed_factor_percent < 10) ? (0) : Math.min(farming_speed_factor_percent, 200);
    processing_command_item_mapping.clear();
    processing_command_item_mapping.put(Items.CRAFTING_TABLE, RecipeType.CRAFTING);
    processing_command_item_mapping.put(Items.FURNACE, RecipeType.SMELTING);
    processing_command_item_mapping.put(Items.BLAST_FURNACE, RecipeType.BLASTING);
    processing_command_item_mapping.put(Items.SMOKER, RecipeType.SMOKING);
    processing_command_item_mapping.put(Items.HOPPER, new ProcessingHandler(Items.HOPPER, (te,done)->te.processHopper(), (te)->false));
    processing_command_item_mapping.put(Items.COMPOSTER, new ProcessingHandler(Items.COMPOSTER, (te,done)->te.processComposter(), RedAntHiveTileEntity::itemPassThroughComposter));
    processing_command_item_mapping.put(Items.SHEARS, new ProcessingHandler(Items.SHEARS, (te,done)->te.processShears(), RedAntHiveTileEntity::processHopper));
    processing_command_item_mapping.put(Items.BREWING_STAND, new ProcessingHandler(Items.BREWING_STAND, RedAntHiveTileEntity::processBrewing, RedAntHiveTileEntity::itemPassThroughBrewing));
    processing_command_item_mapping.put(Items.GRINDSTONE, new ProcessingHandler(Items.GRINDSTONE, RedAntHiveTileEntity::processGrindstone, RedAntHiveTileEntity::itemPassThroughGrindstone));
    if(animal_feeding_speed_percent > 0) {
      processing_command_item_mapping.put(Items.WHEAT, new ProcessingHandler(Items.WHEAT, (te,done)->te.processAnimalFood(Items.WHEAT), (te)->te.itemPassThroughExcept(Items.WHEAT)));
      processing_command_item_mapping.put(Items.WHEAT_SEEDS, new ProcessingHandler(Items.WHEAT_SEEDS, (te,done)->te.processAnimalFood(Items.WHEAT_SEEDS), (te)->te.itemPassThroughExcept(Items.WHEAT_SEEDS)));
      processing_command_item_mapping.put(Items.CARROT, new ProcessingHandler(Items.CARROT, (te,done)->te.processAnimalFood(Items.CARROT), (te)->te.itemPassThroughExcept(Items.CARROT)));
    }
    if(farming_speed_percent > 0) {
      processing_command_item_mapping.put(Items.STONE_HOE, new ProcessingHandler(Items.STONE_HOE, (te,done)->te.processFarming(Items.STONE_HOE), (te)->te.itemPassThroughExcept(Items.BONE_MEAL)));
      processing_command_item_mapping.put(Items.IRON_HOE, new ProcessingHandler(Items.IRON_HOE, (te,done)->te.processFarming(Items.IRON_HOE), (te)->te.itemPassThroughExcept(Items.BONE_MEAL)));
      processing_command_item_mapping.put(Items.DIAMOND_HOE, new ProcessingHandler(Items.DIAMOND_HOE, (te,done)->te.processFarming(Items.DIAMOND_HOE), (te)->te.itemPassThroughExcept(Items.BONE_MEAL)));
      processing_command_item_mapping.put(Items.NETHERITE_HOE, new ProcessingHandler(Items.NETHERITE_HOE, (te,done)->te.processFarming(Items.NETHERITE_HOE), (te)->te.itemPassThroughExcept(Items.BONE_MEAL)));
    }
    ModConfig.log("Hive:" +
      "drop-probability:" + hive_drop_probability_percent + "%" +
      "ant-speed-scaler:" + normal_processing_speed_ant_count_percent + "%" +
      "growth-time:" + hive_growth_latency_s + "s" +
      "sugar-time:" + sugar_boost_time_s + "s"
    );
    ModConfig.log("Animals:" +
      "feeding-speed:" + animal_feeding_speed_percent + "%" +
      "entity-limit:" + animal_feeding_entity_limit +
      "xz-radius:" + animal_feeding_xz_radius + "blk"
    );
    ModConfig.log("Crop farming:" +
      "havesting-speed:" + farming_speed_percent + "%"
    );
    ModConfig.log(
      "Ctrl-Items:" + processing_command_item_mapping.keySet().stream().map(e->e.getRegistryName().getPath()).collect(Collectors.joining(","))
    );
  }

  //--------------------------------------------------------------------------------------------------------------------
  // External events
  //--------------------------------------------------------------------------------------------------------------------

  public static void onGlobalPlayerBlockBrokenEvent(BlockState state, LevelAccessor iworld, BlockPos pos, Player player)
  {
    if((!state.is(Blocks.REDSTONE_ORE) && (!state.is(Blocks.DEEPSLATE_REDSTONE_ORE))) || (iworld.isClientSide()) || !(iworld instanceof Level world)) return;
    if(iworld.getRandom().nextInt(100) >= hive_drop_probability_percent) return;
    Inventories.dropStack(world, Vec3.atCenterOf(pos), new ItemStack(ModContent.HIVE_BLOCK.asItem()));
    world.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.BLOCKS, 1f,1.4f);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveBlock extends StandardBlocks.DirectedWaterLoggable implements StandardEntityBlocks.IStandardEntityBlock<RedAntHiveTileEntity>
  {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

    public RedAntHiveBlock(long config, BlockBehaviour.Properties builder, AABB[] aabbs)
    {
      super(config, builder, aabbs);
      registerDefaultState(super.defaultBlockState().setValue(FACING, Direction.DOWN).setValue(VARIANT, 0));
    }

    @Nullable
    @Override
    public BlockEntityType<RedAntHiveTileEntity> getBlockEntityType()
    { return ModContent.TET_HIVE; }

    @Override
    public boolean isBlockEntityTicking(Level world, BlockState state)
    { return true; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    { return Shapes.block(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder.add(VARIANT)); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return null;
      if(!context.getPlayer().isShiftKeyDown()) state = state.setValue(FACING, Direction.DOWN);
      double[] variant_votes = new double[VARIANT.getPossibleValues().size()];
      int reference_weight=0;
      final Level world = context.getLevel();
      final BlockPos placement_pos = context.getClickedPos();
      for(int x=-1; x<=1; ++x) {
        for(int y=-1; y<=1; ++y) {
          for(int z=-1; z<=1; ++z) {
            if(x==0 && y==0 && z==0) continue;
            Vec3i dirv = new Vec3i(x,y,z);
            BlockPos adj_pos = placement_pos.offset(dirv);
            final double adj_scaler = ((Math.abs(x)+Math.abs(y)+Math.abs(z))==1) ? 1.3 : 1.0;
            final double weight = ((x==0)?1.7:1) * ((y==0)?1.7:1) * ((z==0)?1.7:1) * adj_scaler;
            final BlockState adj_state = world.getBlockState(adj_pos);
            if(!adj_state.isSolidRender(world, adj_pos)) continue;
            reference_weight += weight;
            final String name = adj_state.getBlock().getRegistryName().getPath();
            if(name.contains("sand")) {
              variant_votes[2] += weight;
            } else if(name.contains("dirt") || name.contains("grass")) {
              variant_votes[1] += weight;
            } else {
              variant_votes[0] += weight;
            }
          }
        }
      }
      int best_variant = 0;
      double max_vote = variant_votes[0];
      for(int i=0; i<variant_votes.length; ++i) {
        if(variant_votes[i] > max_vote) {
          best_variant = i;
          max_vote = variant_votes[i];
        }
      }
      max_vote /= Math.max(1.0, reference_weight);
      if(max_vote >= 0.2) {
        state = state.setValue(VARIANT, best_variant);
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
    { BlockEntity te = world.getBlockEntity(pos); return (te instanceof RedAntHiveTileEntity) ? ((RedAntHiveTileEntity)te).comparatorValue() : 0; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    { return new RedAntHiveTileEntity(pos, state); }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
      final BlockEntity ate = world.getBlockEntity(pos);
      if(!(ate instanceof RedAntHiveTileEntity te)) return null;
      return new SimpleMenuProvider(
        (int menu_id, Inventory inventory, Player player) -> new RedAntHiveMenu(menu_id, inventory, te.main_inventory_, ContainerLevelAccess.create(world, pos), te.fields),
        new TranslatableComponent("container." + ModAnthillInside.MODID + ".hive")
      );
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      if(world.isClientSide()) return;
      if((!stack.hasTag()) || (!stack.getOrCreateTag().contains("tedata"))) return;
      CompoundTag te_nbt = stack.getOrCreateTag().getCompound("tedata");
      if(te_nbt.isEmpty()) return;
      final BlockEntity te = world.getBlockEntity(pos);
      if(!(te instanceof RedAntHiveTileEntity)) return;
      ((RedAntHiveTileEntity)te).readnbt(te_nbt, false);
      te.setChanged();
    }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, final BlockEntity te, boolean explosion)
    {
      if(world.isClientSide() || (!(te instanceof RedAntHiveTileEntity))) return Collections.emptyList();
      final CompoundTag te_nbt = ((RedAntHiveTileEntity)te).clear_getnbt();
      ItemStack stack = new ItemStack(asItem());
      if(!te_nbt.isEmpty()) {
        final CompoundTag nbt = new CompoundTag();
        nbt.put("tedata", te_nbt);
        stack.setTag(nbt);
      }
      return Collections.singletonList(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rayTraceResult)
    {
      if(world.isClientSide()) return InteractionResult.SUCCESS;
      final BlockEntity te = world.getBlockEntity(pos);
      if((!(te instanceof RedAntHiveTileEntity)) || ((player instanceof FakePlayer))) return InteractionResult.FAIL;
      player.openMenu((RedAntHiveTileEntity)te);
      return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
    {
      if(world.isClientSide) return;
      BlockEntity te = world.getBlockEntity(pos);
      if(!(te instanceof RedAntHiveTileEntity)) return;
      ((RedAntHiveTileEntity)te).block_updated();
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, LevelReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side)
    {
      final BlockEntity te = world.getBlockEntity(pos);
      return (te instanceof RedAntHiveTileEntity) ? (((RedAntHiveTileEntity)te).getRedstonePower(state, side, false)) : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side)
    {
      final BlockEntity te = world.getBlockEntity(pos);
      return (te instanceof RedAntHiveTileEntity) ? (((RedAntHiveTileEntity)te).getRedstonePower(state, side, true)) : 0;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveTileEntity extends StandardEntityBlocks.StandardBlockEntity implements MenuProvider, Nameable
  {
    // SimpleContainer sections ----------------------------------------------------------------------------------------------
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
      public static final long mask_powered       = ((      1)   );
      public static final long mask_sugared       = (((long)1)<<1);
      public static final long mask_nofuel        = (((long)1)<<2);
      public static final long mask_norecipe      = (((long)1)<<3);
      public static final long mask_noingr        = (((long)1)<<4);
      public static final long mask_nopassthrough = (((long)1)<<5);
      public static final long mask_filteredinsert= (((long)1)<<6);
      public static final long mask_noants        = (((long)1)<<7);

      public StateFlags(int v)             { super(v); }
      public boolean powered()             { return bit(0); }
      public boolean sugared()             { return bit(1); }
      public boolean nofuel()              { return bit(2); }
      public boolean norecipe()            { return bit(3); }
      public boolean noingr()              { return bit(4); }
      public boolean nopassthrough()       { return bit(5); }
      public boolean filteredinsert()      { return bit(6); }
      public boolean noants()              { return bit(7); }
      public void powered(boolean v)       { bit(0, v); }
      public void sugared(boolean v)       { bit(1, v); }
      public void nofuel(boolean v)        { bit(2, v); }
      public void norecipe(boolean v)      { bit(3, v); }
      public void noingr(boolean v)        { bit(4, v); }
      public void nopassthrough(boolean v) { bit(5, v); }
      public void filteredinsert(boolean v){ bit(6, v); }
      public void noants(boolean v)        { bit(7, v); }
    }

    public static final int NUM_OF_FIELDS            =  3;
    public static final int TICK_INTERVAL            =  8;
    public static final int SLOW_INTERVAL            =  2;
    private final StateFlags state_flags_ = new StateFlags(0);
    private String last_recipe_ = "";
    private int colony_growth_progress_ = 0;
    private int sugar_ticks_ = 0;
    private boolean can_use_sugar_ = false;
    private int ant_count_ = 0;
    private double ant_speed_ = 0;
    private double progress_ = 0;
    private int max_progress_ = 0;
    private int universal_task_index_ = 0;
    private int fuel_left_ = 0;
    private int tick_timer_ = 0;
    private int slow_timer_ = 0; // performance timer for slow tasks
    private int input_side_redstone_pulse_time_left_ = 0;
    private int output_side_redstone_pulse_time_left_ = 0;
    private final Map<UUID,Long> entity_handling_cooldowns_ = new HashMap<>(); // not in nbt

    public static BiPredicate<Integer, ItemStack> main_inventory_validator() {
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

    public RedAntHiveTileEntity(BlockPos pos, BlockState state)
    {
      super(ModContent.TET_HIVE, pos, state);
      main_inventory_           =  new Inventories.StorageInventory(this, NUM_MAIN_INVENTORY_SLOTS).setValidator(main_inventory_validator());
      left_storage_slot_range_  =  new Inventories.InventoryRange(main_inventory_, LEFT_STORAGE_START , LEFT_STORAGE_NUM_SLOTS, LEFT_STORAGE_NUM_ROWS);
      right_storage_slot_range_ =  new Inventories.InventoryRange(main_inventory_, RIGHT_STORAGE_START, RIGHT_STORAGE_NUM_SLOTS, RIGHT_STORAGE_NUM_ROWS);
      ant_storage_slot_range_   =  new Inventories.InventoryRange(main_inventory_, ANT_STORAGE_START, ANT_STORAGE_NUM_SLOTS, ANT_STORAGE_NUM_ROWS);
      grid_storage_slot_range_  = (new Inventories.InventoryRange(main_inventory_, GRID_STORAGE_START, GRID_STORAGE_NUM_SLOTS, GRID_STORAGE_NUM_ROWS)).setMaxStackSize(1);
      cache_slot_range_         = (new Inventories.InventoryRange(main_inventory_, CMD_CACHE_START, CMD_CACHE_NUM_SLOTS, CMD_CACHE_NUM_ROWS));
      left_filter_slot_range_   = (new Inventories.InventoryRange(main_inventory_, LEFT_FILTER_START, LEFT_FILTER_NUM_SLOTS, LEFT_FILTER_NUM_ROWS)).setMaxStackSize(1);
      item_handler_ = Inventories.MappedItemHandler.createGenericHandler(
        main_inventory_,
        (slot, stack)->(slot >= right_storage_slot_range_.offset()) && (slot < (right_storage_slot_range_.offset()+right_storage_slot_range_.size())),
        (slot, stack)->(slot >= left_storage_slot_range_.offset()) && (slot < (left_storage_slot_range_.offset()+left_storage_slot_range_.size())) && insertionAllowed(left_storage_slot_range_.offset()+slot, stack)
      );
      state_flags_.nopassthrough(true);
    }

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

    protected void writenbt(CompoundTag nbt, boolean update_packet)
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

    // BlockEntity --------------------------------------------------------------------------------------------

    @Override
    public void load(CompoundTag nbt)
    { super.load(nbt); readnbt(nbt, false); }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    { super.saveAdditional(nbt); writenbt(nbt, false); }

    @Override
    public void setRemoved()
    { super.setRemoved(); item_handler_.invalidate(); }

    @Override
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable Direction facing)
    {
      if(capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return item_handler_.cast();
      return super.getCapability(capability, facing);
    }

    // Namable ------------------------------------------------------------------------------------------------

    @Override
    public Component getName()
    { final Block block=getBlockState().getBlock(); return (block!=null) ? (new TranslatableComponent(block.getDescriptionId())) : (new TextComponent("Hive")); }

    @Override
    public boolean hasCustomName()
    { return false; }

    @Override
    public Component getCustomName()
    { return getName(); }

    // MenuProvider -------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName()
    { return this.getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player )
    { return new RedAntHiveMenu(id, inventory, main_inventory_, ContainerLevelAccess.create(level, worldPosition), fields); }

    protected final ContainerData fields = new ContainerData()
    {
      @Override
      public int getCount()
      { return 3; }
      @Override
      public int get(int id)
      {
        return switch (id) {
          case 0 -> RedAntHiveTileEntity.this.state_flags_.int_value();
          case 1 -> RedAntHiveTileEntity.this.max_progress_;
          case 2 -> (int) RedAntHiveTileEntity.this.progress_;
          default -> 0;
        };
      }
      @Override
      public void set(int id, int value)
      {
        switch(id) {
          case 0: RedAntHiveTileEntity.this.state_flags_.value(value); return;
          case 1: RedAntHiveTileEntity.this.max_progress_ = Math.max(value, 0); return;
          case 2: RedAntHiveTileEntity.this.progress_ = Mth.clamp(value ,0, max_progress_); return;
          default:
        }
      }
    };

    // Tick -------------------------------------------------------------------------------------------------

    @Override
    public void tick()
    {
      // Tick timing
      if(--tick_timer_ > 0) return;
      tick_timer_ = TICK_INTERVAL;
      if(slow_timer_++ >= SLOW_INTERVAL) slow_timer_ = 0;
      // Tick timing
      if(!checkColony()) return;
      boolean dirty = false;
      dirty |= checkItemOutput();
      dirty |= checkWorkProcess();
      dirty |= checkItemInput();
      if(dirty) setChanged();
    }

    // TE specific ------------------------------------------------------------------------------------------

    public int getRedstonePower(BlockState state, Direction redstone_side, boolean strong)
    {
      if(strong) return 0;
      if((input_side_redstone_pulse_time_left_  > 0) && (redstone_side == state.getValue(RedAntHiveBlock.FACING))) return 15;
      if((output_side_redstone_pulse_time_left_ > 0) && (redstone_side == state.getValue(RedAntHiveBlock.FACING).getOpposite())) return 15;
      return 0;
    }

    protected StateFlags getStateFlags()
    { return state_flags_; }

    protected boolean isSlowTimerTick()
    { return slow_timer_ == 0; }

    protected boolean isProcessing()
    { return (progress_ > 0) && (max_progress_ > 0); }

    @Nullable
    BlockState updateBlockstate()
    {
      BlockState state = getBlockState();
      state_flags_.powered(getLevel().hasNeighborSignal(getBlockPos()));
      return state;
    }

    protected void setResultSlot(ItemStack stack)
    { main_inventory_.setItem(CMD_RESULT_START, stack); }

    protected ItemStack getResultSlot()
    { return main_inventory_.getItem(CMD_RESULT_START); }

    protected void setCommandSlot(ItemStack stack)
    { main_inventory_.setItem(CMD_STORAGE_START, stack); }

    protected ItemStack getCommandSlot()
    { return main_inventory_.getItem(CMD_STORAGE_START); }

    protected void setInputControlSlot(ItemStack stack)
    { main_inventory_.setItem(INP_STORAGE_START, stack); }

    protected ItemStack getInputControlSlot()
    { return main_inventory_.getItem(INP_STORAGE_START); }

    protected void setOutputControlSlot(ItemStack stack)
    { main_inventory_.setItem(OUT_STORAGE_START, stack); }

    protected ItemStack getOutputControlSlot()
    { return main_inventory_.getItem(OUT_STORAGE_START); }

    protected boolean insertionAllowed(int index, ItemStack stack)
    {
      if((!state_flags_.filteredinsert())) return true;
      if(left_filter_slot_range_.getItem(index).isEmpty()) return true;
      if(left_filter_slot_range_.getItem(index).getItem() == stack.getItem()) return true;
      return false;
    }

    protected ItemStack insertLeft(ItemStack stack)
    {
      if(!state_flags_.filteredinsert()) return left_storage_slot_range_.insert(stack);
      ItemStack remaining = stack.copy();
      for(int i=0; i<left_storage_slot_range_.getContainerSize(); ++i) {
        if((!left_storage_slot_range_.getItem(i).isEmpty())
        || (left_filter_slot_range_.getItem(i).isEmpty())
        || (left_filter_slot_range_.getItem(i).getItem() == stack.getItem())
      ) {
          remaining = left_storage_slot_range_.insert(i, remaining);
          if(remaining.isEmpty()) break;
        }
      }
      return remaining;
    }

    public void enableInsertionFilter(boolean enable)
    {
      state_flags_.filteredinsert(enable);
      if(!enable) {
        left_filter_slot_range_.clearContent();
      } else {
        for(int i=0; i<left_filter_slot_range_.size(); ++i) {
          ItemStack stack = left_storage_slot_range_.getItem(i);
          if(stack.isEmpty()) {
            left_filter_slot_range_.setItem(i, ItemStack.EMPTY);
          } else {
            stack = stack.copy();
            stack.setCount(1);
            left_filter_slot_range_.setItem(i, stack);
          }
        }
      }
      setChanged();
    }

    private AABB workingRange(int xz_radius, int y_height, int y_offset)
    {
      final Direction facing = getBlockState().getValue(RedAntHiveBlock.FACING).getOpposite();
      AABB aabb = new AABB(-xz_radius, y_offset, -xz_radius, xz_radius+1, y_offset+y_height, xz_radius+1);
      if(facing == Direction.UP) {
        aabb = aabb.move(getBlockPos().above());
      } else if(facing == Direction.DOWN) {
        aabb = aabb.move(getBlockPos().below(y_height));
      } else {
        aabb = aabb.move(getBlockPos().relative(facing, xz_radius+1));
      }
      return aabb;
    }

    private boolean entityCooldownExpired(UUID uuid)
    {
      final long t = entity_handling_cooldowns_.getOrDefault(uuid,0L);
      if(t <= getLevel().getGameTime()) {
        entity_handling_cooldowns_.remove(uuid);
        return true;
      } else {
        return false;
      }
    }

    private void entityCooldown(UUID uuid, int time)
    {
      final long t = getLevel().getGameTime();
      entity_handling_cooldowns_.put(uuid, t+time);
      if(entity_handling_cooldowns_.size() < 128) return;
      entity_handling_cooldowns_.keySet().forEach((k)->{
        if(entity_handling_cooldowns_.getOrDefault(k, 0L) >= t) return;
        entity_handling_cooldowns_.remove(k);
      });
    }

    private boolean checkColony()
    {
      final int max_ants = ant_storage_slot_range_.size() * ant_storage_slot_range_.getMaxStackSize();
      ant_count_ = ant_storage_slot_range_.stream().filter(e->e.getItem()==ANTS_ITEM).mapToInt(ItemStack::getCount).sum();
      state_flags_.noants(ant_count_ == 0);
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
      if(output_side_redstone_pulse_time_left_ > 0) {
        if((output_side_redstone_pulse_time_left_ -= TICK_INTERVAL) <= 0) {
          output_side_redstone_pulse_time_left_ = 0;
          level.neighborChanged(getBlockPos().relative(getBlockState().getValue(RedAntHiveBlock.FACING)), getBlockState().getBlock(), getBlockPos());
        }
      }
      if(state_flags_.powered()) return false;
      if(getOutputControlSlot().isEmpty()) return false;
      final int outstack_size = 1 + (ant_count_/96);
      final Item control_item = getOutputControlSlot().getItem();
      final Direction output_facing = getBlockState().getValue(RedAntHiveBlock.FACING);
      final BlockPos output_position = getBlockPos().relative(output_facing);
      if(control_item == Items.HOPPER) {
        if(Inventories.insertionPossible(getLevel(), output_position, output_facing.getOpposite(), true)) {
          for(int slot=0; slot<right_storage_slot_range_.size(); ++slot) {
            ItemStack ostack = right_storage_slot_range_.getItem(slot);
            if(ostack.isEmpty()) continue;
            final ItemStack stack = ostack.copy();
            if(stack.getCount() > outstack_size) stack.setCount(outstack_size);
            final ItemStack remaining = Inventories.insert(getLevel(), output_position, output_facing.getOpposite(), stack, false, true);
            if(remaining.getCount() == stack.getCount()) continue;
            final int n_inserted = stack.getCount() - remaining.getCount();
            right_storage_slot_range_.getItem(slot).shrink(n_inserted);
            return true; // TE dirty.
          }
          if(right_storage_slot_range_.isEmpty()) {
            if((Inventories.itemhandler(getLevel(), output_position, output_facing.getOpposite(), false) == null)) {
              // SimpleContainer entity eg minecart.
              boolean notify = output_side_redstone_pulse_time_left_<= 0;
              output_side_redstone_pulse_time_left_ = 15;
              if(notify) level.neighborChanged(getBlockPos().relative(output_facing), getBlockState().getBlock(), getBlockPos());
            }
          }
        } else {
          final BlockState trail_state = getLevel().getBlockState(output_position);
          if(!trail_state.is(ModContent.TRAIL_BLOCK)) return false;
          if((output_facing == trail_state.getValue(RedAntTrailBlock.HORIZONTAL_FACING)) || (output_facing==Direction.DOWN && trail_state.getValue(RedAntTrailBlock.UP))) return false;
          Inventories.dropStack(getLevel(), Vec3.atCenterOf(output_position).add(0,-.4,0), right_storage_slot_range_.extract(1), new Vec3(0, -0.2, 0), 0.1, 0.1);
          return true;
        }
      } else if((control_item == Items.DROPPER) || (control_item == Items.DISPENSER)) {
        final BlockState state = getLevel().getBlockState(output_position);
        if(state.isCollisionShapeFullBlock(getLevel(), output_position)) return false;
        ItemStack stack = right_storage_slot_range_.extract(1, true);
        if(control_item == Items.DISPENSER) {
          Vec3 drop_pos = Vec3.atCenterOf(output_position).add(Vec3.atLowerCornerOf(output_facing.getOpposite().getNormal()).scale(0.3));
          Vec3 speed = Vec3.atLowerCornerOf(output_facing.getNormal()).scale(0.6);
          Inventories.dropStack(getLevel(), drop_pos, stack, speed, 0.1, 0.2);
        } else {
          Vec3 drop_pos = Vec3.atCenterOf(output_position).add(Vec3.atLowerCornerOf(output_facing.getOpposite().getNormal()).scale(0.1));
          Vec3 speed = Vec3.atLowerCornerOf(output_facing.getNormal()).scale(0.05);
          Inventories.dropStack(getLevel(), drop_pos, stack, speed, 0.3,0.02);
        }
      }
      return false;
    }

    private boolean checkItemInput()
    {
      final Direction input_facing = getBlockState().getValue(RedAntHiveBlock.FACING).getOpposite();
      if(input_side_redstone_pulse_time_left_ > 0) {
        if((input_side_redstone_pulse_time_left_ -= TICK_INTERVAL) <= 0) {
          input_side_redstone_pulse_time_left_ = 0;
          level.neighborChanged(getBlockPos().relative(input_facing), getBlockState().getBlock(), getBlockPos());
        }
      }
      if(getInputControlSlot().getItem() != Items.HOPPER) return false;
      final int instack_size = 1 + (ant_count_/96);
      final boolean filtered = state_flags_.filteredinsert();
      boolean dirty = false;
      // Item handler
      {
        final IItemHandler ih = Inventories.itemhandler(getLevel(), getBlockPos().relative(input_facing), input_facing.getOpposite(), true);
        if(ih!=null) {
          for(int i=0; i<left_storage_slot_range_.size(); ++i) {
            ItemStack ref_stack = left_storage_slot_range_.getItem(i);
            if((ref_stack.getCount() >= ref_stack.getMaxStackSize()) || (ref_stack.getCount() >= (left_storage_slot_range_.getMaxStackSize()))) continue;
            if(ref_stack.isEmpty()) {
              if(filtered) ref_stack = left_filter_slot_range_.getItem(i);
              final ItemStack fetched = Inventories.extract(ih, ref_stack.isEmpty() ? null : ref_stack, instack_size, false);
              if(!fetched.isEmpty()) {
                insertLeft(fetched);
                return true;
              }
            } else {
              final int limit = Math.min(instack_size, ref_stack.getMaxStackSize() - ref_stack.getCount());
              final ItemStack fetched = Inventories.extract(ih, ref_stack, limit, false);
              if(!fetched.isEmpty()) {
                insertLeft(fetched);
                return true;
              }
            }
          }
          if((Inventories.itemhandler(getLevel(), getBlockPos().relative(input_facing), input_facing.getOpposite(), false) == null)) {
            // Nothing inserted from inventory entity eg minecart.
            boolean all_empty = true;
            for(int i=0; (all_empty) && (i<ih.getSlots()); ++i) all_empty = ih.getStackInSlot(i).isEmpty();
            if(all_empty) {
              boolean notify = input_side_redstone_pulse_time_left_ <= 0;
              input_side_redstone_pulse_time_left_ = 15;
              if(notify) level.neighborChanged(getBlockPos().relative(input_facing), getBlockState().getBlock(), getBlockPos());
            }
          }
        }
      }
      // Item entities
      {
        final Vec3 dvec = Vec3.atLowerCornerOf(input_facing.getNormal());
        final AABB aabb = (new AABB(getBlockPos())).inflate(0.3).move(dvec.scale(0.3)).expandTowards(dvec.scale(1.0));
        final List<ItemEntity> items = getLevel().getEntitiesOfClass(ItemEntity.class, aabb, (e)->e.isAlive() && (!e.getItem().isEmpty()));
        for(ItemEntity ie:items) {
          final ItemStack stack = ie.getItem().copy();
          final ItemStack remaining = insertLeft(stack);
          if(remaining.getCount() == stack.getCount()) continue;
          dirty = true;
          if(remaining.isEmpty()) {
            ie.remove(Entity.RemovalReason.DISCARDED);
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
        fuel_left_ = 0;
        state_flags_.mask(StateFlags.mask_nofuel|StateFlags.mask_norecipe|StateFlags.mask_noingr, 0);
        cache_slot_range_.move(left_storage_slot_range_);
        if(!getResultSlot().isEmpty()) {
          setResultSlot(ItemStack.EMPTY);
          entity_handling_cooldowns_.clear();
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
        if(cat instanceof RecipeType<?>) {
          if(cat == RecipeType.CRAFTING) {
            processCrafting((RecipeType<?>)cat, is_done);
          } else if((cat == RecipeType.SMELTING) || (cat == RecipeType.BLASTING) || (cat == RecipeType.SMOKING)) {
            processFurnace((RecipeType<?>)cat, is_done);
          }
        } else if(cat instanceof ProcessingHandler) {
          return ((ProcessingHandler)cat).handler.apply(this, is_done);
        }
      }
      return true;
    }

    private boolean itemPassThrough(Object type)
    {
      if(state_flags_.nopassthrough()) return false;
      if(!isSlowTimerTick()) return false;
      if(type == RecipeType.CRAFTING) {
        return itemPassThroughCrafting((RecipeType<?>)type);
      } else if((type == RecipeType.SMELTING) || (type == RecipeType.BLASTING) || (type == RecipeType.SMOKING)) {
        return itemPassThroughFurnace((RecipeType<?>)type);
      } else if(type instanceof ProcessingHandler) {
        return ((ProcessingHandler)type).passthrough_handler.apply(this);
      } else {
        return false;
      }
    }

    private boolean itemPassThroughExcept(Item except)
    { return itemPassThroughExcept(Collections.singletonList(except)); }

    private boolean itemPassThroughExcept(List<Item> except)
    {
      for(int i=0; i<left_storage_slot_range_.size(); ++i) {
        ItemStack stack = left_storage_slot_range_.getItem(i);
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM) || (except.contains(stack.getItem()))) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) return true;
      }
      return false;
    }

    private boolean processCrafting(RecipeType<?> recipe_type, boolean is_done)
    {
      progress_ = -40; // Default "cannot process", needs waiting time.
      if(is_done) {
        setResultSlot(ItemStack.EMPTY);
        List<CraftingRecipe> crafting_recipes;
        if(!last_recipe_.isEmpty()) {
          crafting_recipes = Crafting.getCraftingRecipe(getLevel(), new ResourceLocation(last_recipe_)).map(Collections::singletonList).orElse(Collections.emptyList());
        } else {
          crafting_recipes = Crafting.get3x3CraftingRecipes(getLevel(), cache_slot_range_);
        }
        if(!crafting_recipes.isEmpty()) {
          final List<ItemStack> remaining = Crafting.get3x3RemainingItems(getLevel(), cache_slot_range_, crafting_recipes.get(0));
          final ItemStack result = Crafting.get3x3CraftingResult(getLevel(), cache_slot_range_, crafting_recipes.get(0));
          cache_slot_range_.clearContent();
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
        } // else -> cache will be transferred anyway next cycle.
        progress_ = -TICK_INTERVAL;
        return true;
      } else {
        if(!cache_slot_range_.isEmpty()) return false;
        // Initiate new crafting process
        List<CraftingRecipe> crafting_recipes = Crafting.get3x3CraftingRecipes(getLevel(), grid_storage_slot_range_);
        if(!crafting_recipes.isEmpty() && !last_recipe_.isEmpty()) {
          List<CraftingRecipe> last = Crafting.getCraftingRecipe(getLevel(), new ResourceLocation(last_recipe_)).map(Collections::singletonList).orElse(Collections.emptyList());
          if(!last.isEmpty() && crafting_recipes.contains(last.get(0))) {
            crafting_recipes = last;
          }
        }
        if(crafting_recipes.isEmpty()) {
          state_flags_.norecipe(true);
          last_recipe_ = "";
          return false;
        }
        CraftingRecipe selected_recipe = null;
        List<ItemStack> selected_placement = Collections.emptyList();
        for(CraftingRecipe recipe:crafting_recipes) {
          final List<ItemStack> placement = Crafting.get3x3Placement(getLevel(), recipe, left_storage_slot_range_, grid_storage_slot_range_);
          if(placement.isEmpty()) continue;
          selected_recipe = recipe;
          selected_placement = placement;
          break;
        }
        if(selected_placement.isEmpty()) {
          state_flags_.noingr(true);
          return false;
        }
        cache_slot_range_.clearContent();
        for(int i=0; i<selected_placement.size(); ++i) {
          cache_slot_range_.setItem(i, left_storage_slot_range_.extract(selected_placement.get(i)));
        }
        setResultSlot(Crafting.get3x3CraftingResult(getLevel(), cache_slot_range_, selected_recipe));
        last_recipe_ = selected_recipe.getId().toString();
        progress_ = 0;
        max_progress_ = 60 + (4*cache_slot_range_.stream().mapToInt(ItemStack::getCount).sum());
        return true;
      }
    }

    private boolean itemPassThroughCrafting(RecipeType<?> recipe_type)
    {
      if(last_recipe_.isEmpty()) return false;
      final CraftingRecipe recipe = Crafting.getCraftingRecipe(getLevel(), new ResourceLocation(last_recipe_)).orElse(null);
      if(recipe==null) return false;
      final List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ing->(ing!=Ingredient.EMPTY)).collect(Collectors.toList());
      if(ingredients.isEmpty()) return false;
      for(int i=0; i<left_storage_slot_range_.size(); ++i) {
        final ItemStack stack = left_storage_slot_range_.getItem(i);
        if(stack.getItem() == RED_SUGAR_ITEM) continue;
        if(ingredients.stream().noneMatch(ing->ing.test(stack))) {
          if(left_storage_slot_range_.move(i,right_storage_slot_range_)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean processFurnace(RecipeType<?> recipe_type, boolean is_done)
    {
      last_recipe_ = "";
      progress_ = -40; // Default "cannot process or transfer", needs waiting time.
      if(is_done) {
        if(!getResultSlot().isEmpty()) {
          cache_slot_range_.setItem(0, getResultSlot());
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
        Tuple<Integer, AbstractCookingRecipe> recipe_search;
        // Determine recipe or abort
        {
          recipe_search = input_slots.find((slot,stack)->{
            // Recipes for items that are not fuel.
            if(Crafting.isFuel(level, stack)) return Optional.empty();
            Optional<AbstractCookingRecipe> recipe = Crafting.getFurnaceRecipe(recipe_type, level, stack);
            return recipe.map(r->new Tuple<>(slot, r));
          }).orElse(null);
          if(recipe_search == null) {
            // Recipes for items that are also fuel, but not in the fuel slot list.
            recipe_search = input_slots.find((slot,stack)->{
              if((!allowed_fuel.isEmpty()) && (!allowed_fuel.contains(stack.getItem()))) return Optional.empty();
              Optional<AbstractCookingRecipe> recipe = Crafting.getFurnaceRecipe(recipe_type, level, stack);
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
        final int fuel_time_needed = (int)Math.ceil((1.0+ant_speed_) * recipe.getCookingTime());
        // Collect fuel
        {
          final int initial_fuel_left = fuel_left_;
          final boolean enough_fuel = input_slots.iterate((slot,stack)->{
            if(fuel_left_ >= fuel_time_needed) return true;
            if((slot == smelting_input_slot) || (stack.isEmpty())) return false;
            int t = Crafting.getFuelBurntime(level, stack);
            if(t <= 0) return false;
            if((!allowed_fuel.isEmpty()) && (!allowed_fuel.contains(stack.getItem()))) return false;
            while((stack.getCount() > 0) && (fuel_left_ < fuel_time_needed)) {
              final ItemStack consumed = stack.split(1);
              Tuple<Integer,ItemStack> bt_and_rem = Crafting.consumeFuel(getLevel(), consumed);
              fuel_left_ += bt_and_rem.getA();
              if(!bt_and_rem.getB().isEmpty()) {
                final ItemStack container_item = bt_and_rem.getB();
                if(stack.isEmpty()) {
                  stack = container_item;
                } else {
                  if(!right_storage_slot_range_.insert(container_item).isEmpty()) {
                    cache_slot_range_.insert(container_item);
                  }
                }
              }
            }
            if(stack.isEmpty()) stack = ItemStack.EMPTY;
            input_slots.setItem(slot, stack);
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
          setResultSlot(recipe.getResultItem());
          fuel_left_ -= fuel_time_needed;
          max_progress_ = recipe.getCookingTime();
          last_recipe_ = recipe.getId().toString();
          progress_ = 0;
          cache_slot_range_.setItem(0, input_slots.getItem(smelting_input_slot).split(1));
          return true;
        }
      }
    }

    private boolean itemPassThroughFurnace(RecipeType<?> recipe_type)
    {
      final List<Item> allowed_fuel = grid_storage_slot_range_.stream().filter(e->!e.isEmpty()).map(ItemStack::getItem).collect(Collectors.toList());
      final List<Integer> unmatching_slots = left_storage_slot_range_.collect((slot,stack)->{
        // Recipes for items that are not fuel.
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM)) return Optional.empty();
        if(Crafting.isFuel(level, stack) && (allowed_fuel.isEmpty() || allowed_fuel.contains(stack.getItem()))) return Optional.empty();
        if(Crafting.getFurnaceRecipe(recipe_type, level, stack).isPresent()) return Optional.empty();
        return Optional.of(slot);
      });
      // Randomly pick a slot among the eligable and try to move that to the output side.
      if(unmatching_slots.isEmpty()) return false;
      final int picked_slot = unmatching_slots.get(level.getRandom().nextInt(unmatching_slots.size()));
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
          if(((getLevel().getRandom().nextDouble()) < chance)) continue;
          if((--fuel_left_) < 0) {
            if(!right_storage_slot_range_.insert(bonemeal).isEmpty()) cache_slot_range_.insert(bonemeal);
            fuel_left_ = 8;
          }
        }
        if(processed > 0) {
          state_flags_.noingr(false);
          progress_ = 0;
          max_progress_ = 20 + (processed*10);
          setResultSlot(bonemeal);
          return true;
        }
      }
      setResultSlot(ItemStack.EMPTY);
      progress_ = -40;
      state_flags_.noingr(true);
      return false;
    }

    private boolean itemPassThroughComposter()
    {
      if(!cache_slot_range_.isEmpty()) return false;
      for(int i=0; i<left_storage_slot_range_.size(); ++i) {
        final ItemStack stack = left_storage_slot_range_.getItem(i);
        if(stack.isEmpty() || (Crafting.getCompostingChance(stack)>0) || (stack.getItem()==RED_SUGAR_ITEM)) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) return true;
      }
      return false;
    }

    private boolean processHopper()
    {
      progress_ = 0;
      max_progress_ = TICK_INTERVAL;
      return itemPassThroughExcept(Collections.emptyList());
    }

    private boolean processShears()
    {
      final Direction input_facing = getBlockState().getValue(RedAntHiveBlock.FACING).getOpposite();
      boolean snipped = ToolActions.shearPlant(getLevel(), getBlockPos().relative(input_facing));
      if(!snipped) {
        AABB aabb = new AABB(getBlockPos().relative(input_facing));
        snipped = ToolActions.shearEntities(getLevel(), aabb, 1);
      }
      if(snipped) {
        progress_ = 0;
        max_progress_ = 120;
      } else {
        progress_ = -40;
        max_progress_ = 0;
      }
      return false;
    }

    private boolean processAnimalFood(Item food)
    {
      if(animal_feeding_speed_percent <= 0) { progress_ = -40; max_progress_ = 0; return false; }
      progress_ = 0;
      max_progress_ = 200 * 100/animal_feeding_speed_percent;
      final ItemStack kibble = new ItemStack(food, 2);
      if(left_storage_slot_range_.stream().filter(s->s.sameItem(kibble)).mapToInt(ItemStack::getCount).sum() <= 0) return false;
      final AABB aabb = workingRange(animal_feeding_xz_radius, 2, 0);
      List<Animal> animals = getLevel().getEntitiesOfClass(Animal.class, aabb, LivingEntity::isAlive);
      final double progress_delay = 1.0/Mth.clamp(1.0-(Math.max(animals.size()*animals.size(), 1.0)/Math.max(animal_feeding_entity_limit*animal_feeding_entity_limit, 16.0)), 0.05,1.0);
      Auxiliaries.logDebug("" + getBlockPos() + ": animals:" + animals.size() + "/" + animal_feeding_entity_limit + " feeding delay rate:" + progress_delay);
      if(animals.size() >= animal_feeding_entity_limit) { max_progress_ = (int)(600*progress_delay); return false; }
      animals = animals.stream().filter(a->(!a.isBaby()) && (a.isFood(kibble)) && (a.canFallInLove()) && (!a.isInLove()) && entityCooldownExpired(a.getUUID())).collect(Collectors.toList());
      if(animals.size() >= 2) {
        for(int i=0; i<animals.size()-1; ++i) {
          for(int j=i+1; j<animals.size(); ++j) {
            if(animals.get(i).getClass() == animals.get(j).getClass()) {
              left_storage_slot_range_.extract(kibble);
              animals.get(i).setInLove(null);
              animals.get(j).setInLove(null);
              final int cooldown = (int)(20*600* 100/animal_feeding_speed_percent / progress_delay);
              entityCooldown(animals.get(i).getUUID(), cooldown);
              entityCooldown(animals.get(j).getUUID(), cooldown);
              max_progress_ = (int)(600 * progress_delay);
              return false;
            }
          }
        }
      }
      max_progress_ = (int)(max_progress_ * progress_delay);
      return false;
    }

    private boolean processFarming(Item hoe)
    {
      if(farming_speed_percent <= 0) { progress_ = -40; max_progress_ = 0; return false; }
      progress_ = 0;
      max_progress_ = 300 * 100/farming_speed_percent;
      boolean dirty = false;
      @SuppressWarnings("deprecation")
      final int range_ref = Mth.clamp(((hoe instanceof TieredItem) ? (((TieredItem)hoe).getTier().getLevel()):0), 0, 4);
      final int range_rad = range_ref+1;
      final Auxiliaries.BlockPosRange range = Auxiliaries.BlockPosRange.of(workingRange(range_rad, 1, 0));
      final int[] step_sizes = {5,13,31,47,71};
      final int step_size = step_sizes[range_ref];
      final int volume = range.getVolume();
      final int max_count = (range_ref/2) + 1;
      final int max_search_count = 5;
      final @Nullable Tag<Item> fertilizers = ItemTags.getAllTags().getTag(new ResourceLocation(ModAnthillInside.MODID, "fertilizers"));
      int fertilizer_slot = -1;
      if(fertilizers!=null) {
        fertilizer_slot = (left_storage_slot_range_.find((slot,stack)->stack.is(fertilizers) ? Optional.of(slot) : Optional.empty()).orElse(-1));
      }
      if(fertilizer_slot < 0) {
        fertilizer_slot = (left_storage_slot_range_.find((slot,stack)->stack.getItem() == Items.BONE_MEAL ? Optional.of(slot) : Optional.empty()).orElse(-1));
      }
      final ItemStack fertilizer = (fertilizer_slot>=0) ? left_storage_slot_range_.getItem(fertilizer_slot).copy() : ItemStack.EMPTY;
      for(int search_count = max_search_count; search_count > 0; --search_count) {
        for(int i=0; i<max_count; ++i) {
          universal_task_index_ = (universal_task_index_ + step_size) % volume;
          final BlockPos pos = range.byXZYIndex(universal_task_index_);
          final Optional<List<ItemStack>> drops = ToolActions.harvestCrop((ServerLevel)getLevel(), pos, true, fertilizer);
          if(!drops.isPresent()) continue;
          if(!drops.get().isEmpty()) getLevel().playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.6f, 1.4f);
          for(ItemStack stack:drops.get()) right_storage_slot_range_.insert(stack); // skip/void excess
          search_count = 0;
        }
      }
      if(fertilizer_slot >= 0) {
        left_storage_slot_range_.setItem(fertilizer_slot, fertilizer.isEmpty() ? ItemStack.EMPTY : fertilizer);
      }
      return dirty;
    }

    private boolean processBrewing(boolean is_done)
    {
      last_recipe_ = "";
      progress_ = -40;
      if(is_done) {
        if(!getResultSlot().isEmpty()) {
          cache_slot_range_.setItem(0, getResultSlot()); // replace input potion with result
          cache_slot_range_.setItem(1, ItemStack.EMPTY); // consume ingredient
          setResultSlot(ItemStack.EMPTY);
        }
        cache_slot_range_.move(right_storage_slot_range_);
        if(cache_slot_range_.isEmpty()) progress_ = 0;
        state_flags_.nofuel(false);
        state_flags_.noingr(false);
        return true;
      } else {
        final Inventories.InventoryRange input_slots = left_storage_slot_range_;
        Crafting.BrewingOutput brewing_output = Crafting.BrewingOutput.find(level, input_slots, input_slots);
        if(brewing_output.item.isEmpty()) {
          // No ingredients or potions.
          state_flags_.noingr(true);
          return false;
        }
        final int fuel_time_needed = (int)Math.ceil((1.0+ant_speed_) * brewing_output.brewTime);
        // Collect fuel
        {
          final int initial_fuel_left = fuel_left_;
          final boolean enough_fuel = input_slots.iterate((slot,stack)->{
            if(fuel_left_ >= fuel_time_needed) return true;
            if((slot == brewing_output.ingredientSlot) || (slot == brewing_output.potionSlot) || (stack.isEmpty())) return false;
            int t = Crafting.getBrewingFuelBurntime(level, stack) * brewing_fuel_efficiency_percent / 100;
            if(t <= 0) return false;
            while((stack.getCount() > 0) && (fuel_left_ < fuel_time_needed)) {
              final ItemStack consumed = stack.split(1);
              Tuple<Integer,ItemStack> bt_and_rem = Crafting.consumeBrewingFuel(getLevel(), consumed);
              fuel_left_ += bt_and_rem.getA();
              if(!bt_and_rem.getB().isEmpty()) {
                final ItemStack container_item = bt_and_rem.getB();
                if(stack.isEmpty()) {
                  stack = container_item;
                } else {
                  if(!right_storage_slot_range_.insert(container_item).isEmpty()) {
                    cache_slot_range_.insert(container_item);
                  }
                }
              }
            }
            if(stack.isEmpty()) stack = ItemStack.EMPTY;
            input_slots.setItem(slot, stack);
            return false;
          });
          if(!enough_fuel) {
            // Fuel insufficient
            state_flags_.nofuel(true);
            return (fuel_left_ != initial_fuel_left);
          }
        }
        // Start processing.
        {
          if(fuel_left_ < fuel_time_needed) return true;
          state_flags_.nofuel(false);
          state_flags_.norecipe(false);
          setResultSlot(brewing_output.item);
          fuel_left_ -= fuel_time_needed;
          max_progress_ = brewing_output.brewTime;
          progress_ = 0;
          cache_slot_range_.setItem(0, input_slots.getItem(brewing_output.potionSlot).split(1));
          cache_slot_range_.setItem(1, input_slots.getItem(brewing_output.ingredientSlot).split(1));
          return true;
        }
      }
    }

    private boolean itemPassThroughBrewing()
    {
      if(!cache_slot_range_.isEmpty()) return false;
      boolean changed = false;
      for(int i=0; i<left_storage_slot_range_.size(); ++i) {
        final ItemStack stack = left_storage_slot_range_.getItem(i);
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM) ||
          (Crafting.isBrewingFuel(level, stack)) ||
          (Crafting.isBrewingIngredient(level, stack)) ||
          (Crafting.isBrewingInput(level, stack))
        ) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) {
          if(left_storage_slot_range_.getItem(i).isEmpty()) return true;
          changed = true;
        }
      }
      return changed;
    }

    private boolean processGrindstone(boolean is_done)
    {
      last_recipe_ = "";
      progress_ = -40;
      fuel_left_ = 0;
      if(is_done) {
        if(!getResultSlot().isEmpty()) {
          ItemStack input_stack = cache_slot_range_.getItem(0);
          cache_slot_range_.setItem(0, getResultSlot()); // replace input potion with result
          setResultSlot(ItemStack.EMPTY);
          List<ItemStack> enchanted_books = Crafting.removeEnchantmentsOnItem(level, input_stack, (e, l)->true)
            .entrySet().stream()
            .filter(e->!(e.getKey().isCurse()))
            .sorted(Comparator.comparingInt(Map.Entry::getValue))
            .map(e->Crafting.getEnchantmentBook(level, e.getKey(), e.getValue()))
            .filter(e->!e.isEmpty())
            .limit(cache_slot_range_.size()-1)
            .collect(Collectors.toList());
          for(int i=0; i<cache_slot_range_.size(); ++i) {
            if(cache_slot_range_.getItem(i).getItem()!=Items.BOOK) continue;
            if(enchanted_books.isEmpty()) break;
            cache_slot_range_.setItem(i, enchanted_books.remove(0));
          }
        }
        cache_slot_range_.move(right_storage_slot_range_);
        if(cache_slot_range_.isEmpty()) progress_ = 0;
        state_flags_.nofuel(false);
        state_flags_.noingr(false);
        return true;
      } else {
        final Inventories.InventoryRange input_slots = left_storage_slot_range_;
        int input_slot = -1;
        ItemStack input_stack = ItemStack.EMPTY;
        ItemStack output_stack = ItemStack.EMPTY;
        List<ItemStack> secondary_output_stacks = new ArrayList<>();
        int additional_processing_time = 0;
        // Check disenchanting
        {
          final Optional<Tuple<Integer,ItemStack>> match = input_slots.find((slot,stack)->
            Crafting.getEnchantmentsOnItem(level, stack).isEmpty() ? Optional.empty() : Optional.of(new Tuple<>(slot,stack))
          );
          if(match.isPresent()) {
            input_slot = match.get().getA();
            input_stack = match.get().getB();
            output_stack = input_stack.copy();
            List<ItemStack> enchanted_books = Crafting.removeEnchantmentsOnItem(level, output_stack, (e, l)->true)
              .entrySet().stream()
              .filter(e->!e.getKey().isCurse())
              .sorted(Comparator.comparingInt(Map.Entry::getValue))
              .map(e->Crafting.getEnchantmentBook(level, e.getKey(), e.getValue()))
              .filter(e->!e.isEmpty())
              .limit(cache_slot_range_.size()-1)
              .collect(Collectors.toList());
            //.forEach(stack->{ not accepted due to not final `additional_processing_time`})
            for(ItemStack enchanted_book:enchanted_books) {
              if(input_slots.extract(new ItemStack(Items.BOOK,1)).isEmpty()) break;
              secondary_output_stacks.add(new ItemStack(Items.BOOK));
              additional_processing_time += (Crafting.getEnchantmentRepairCost(level, Crafting.getEnchantmentsOnItem(level, enchanted_book))+1) * 160;
            }
          }
        }
        if((input_slot<0) || (input_stack.isEmpty())) {
          // No ingredients or potions.
          state_flags_.noingr(true);
          return false;
        }
        // Start processing.
        {
          state_flags_.nofuel(false);
          state_flags_.norecipe(false);
          setResultSlot(output_stack);
          max_progress_ = 60 + additional_processing_time;
          progress_ = 0;
          cache_slot_range_.setItem(0, input_slots.getItem(input_slot).split(1));
          for(int i=0; i<secondary_output_stacks.size(); ++i) {
            if(i >= (cache_slot_range_.size()-1)) break;
            cache_slot_range_.setItem(1+i, secondary_output_stacks.get(i));
          }
          return true;
        }
      }
    }

    private boolean itemPassThroughGrindstone()
    {
      if(!cache_slot_range_.isEmpty()) return false;
      boolean changed = false;
      for(int i=0; i<left_storage_slot_range_.size(); ++i) {
        final ItemStack stack = left_storage_slot_range_.getItem(i);
        if(stack.isEmpty() || (stack.getItem()==RED_SUGAR_ITEM) ||
          (stack.getItem()==Items.BOOK) ||
          (!Crafting.getEnchantmentsOnItem(level, stack).isEmpty())
        ) continue;
        if(left_storage_slot_range_.move(i, right_storage_slot_range_)) {
          if(left_storage_slot_range_.getItem(i).isEmpty()) return true;
          changed = true;
        }
      }
      return changed;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Container
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntHiveMenu extends AbstractContainerMenu implements Networking.INetworkSynchronisableContainer
  {
    private final RedAntHiveTileEntity te_;
    private final Player player_;
    private final ContainerLevelAccess wpc_;
    private final ContainerData fields_;
    private final Inventories.InventoryRange block_inventory_;
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
    private final int storage_slot_count;


    public RedAntHiveMenu(int windowId, Inventory player_inventory)
    { this(windowId, player_inventory, new SimpleContainer(RedAntHiveTileEntity.NUM_MAIN_INVENTORY_SLOTS), ContainerLevelAccess.NULL, new SimpleContainerData(3)); }

    private RedAntHiveMenu(int windowId, Inventory player_inventory, Container block_inventory, ContainerLevelAccess wpc, ContainerData fields)
    {
      super(ModContent.CT_HIVE, windowId);
      wpc_ = wpc;
      player_ = player_inventory.player;
      fields_ = fields;
      player_inventory_range_ = Inventories.InventoryRange.fromPlayerInventory(player_);
      block_inventory_ = new Inventories.InventoryRange(block_inventory);
      block_inventory_.setValidator(RedAntHiveTileEntity.main_inventory_validator());
      left_storage_slot_range_  =  new Inventories.InventoryRange(block_inventory_, RedAntHiveTileEntity.LEFT_STORAGE_START , RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS);
      left_filter_slot_range_   =  new Inventories.InventoryRange(block_inventory_, RedAntHiveTileEntity.LEFT_FILTER_START , RedAntHiveTileEntity.LEFT_FILTER_NUM_SLOTS, RedAntHiveTileEntity.LEFT_FILTER_NUM_ROWS);
      right_storage_slot_range_ =  new Inventories.InventoryRange(block_inventory_, RedAntHiveTileEntity.RIGHT_STORAGE_START, RedAntHiveTileEntity.RIGHT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS);
      grid_storage_slot_range_  =  new Inventories.InventoryRange(block_inventory_, RedAntHiveTileEntity.GRID_STORAGE_START, RedAntHiveTileEntity.GRID_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS);
      ant_slot_range_ =  new Inventories.InventoryRange(block_inventory_, RedAntHiveTileEntity.ANT_STORAGE_START, RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS, RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS);
      te_ = wpc_.evaluate((w,p)->{
        block_inventory_.startOpen(player_);
        final BlockEntity te = w.getBlockEntity(p);
        return (te instanceof RedAntHiveTileEntity) ? ((RedAntHiveTileEntity)te) : (null);
      }).orElse(null);

      int i = -1;
      // left storage slots
      for(int y = 0; y<RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.LEFT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 8+x*18, ypos = 36+y*18;
          StorageSlot slot = new StorageSlot(block_inventory_, ++i, xpos, ypos);
          final int inventory_index = i;
          if(te_ != null) {
            slot.setSlotChangeNotifier((old_stack, new_stack) -> {
              if(!old_stack.isEmpty() || new_stack.isEmpty()) return;
              if((!te_.getStateFlags().filteredinsert()) || (inventory_index<0) || (inventory_index>=left_storage_slot_range_.size())) return;
              ItemStack filter_stack = new_stack.copy();
              filter_stack.setCount(1);
              left_filter_slot_range_.setItem(inventory_index, filter_stack);
              sync();
            });
          }
          addSlot(slot);
        }
      }
      // right storage slots
      for(int y = 0; y<RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.RIGHT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.RIGHT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 134+x*18, ypos = 18+y*18;
          addSlot(new StorageSlot(block_inventory_, ++i, xpos, ypos));
        }
      }
      // ant storage slots
      for(int y = 0; y<RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS; ++y) {
        for(int x = 0; x<(RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.ANT_STORAGE_NUM_ROWS); ++x) {
          int xpos = 62+x*18, ypos = 18 + y*18;
          addSlot(new StorageSlot(block_inventory_, ++i, xpos, ypos));
        }
      }
      // cmd storage slot
      {
        int xpos = 70, ypos = 50;
        addSlot(command_slot = (new StorageSlot(block_inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // grid storage slots
      {
        List<StorageSlot> slots = new ArrayList<>();
        for(int y = 0; y<RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS; ++y) {
          for(int x = 0; x<(RedAntHiveTileEntity.GRID_STORAGE_NUM_SLOTS/RedAntHiveTileEntity.GRID_STORAGE_NUM_ROWS); ++x) {
            int xpos = 62+x*18, ypos = 72+y*18;
            StorageSlot slot = (new StorageSlot(block_inventory_, ++i, xpos, ypos)).setSlotStackLimit(1);
            slots.add(slot);
            addSlot(slot);
          }
        }
        grid_slots = ImmutableList.copyOf(slots);
      }
      // input selection storage slot
      {
        int xpos = 26, ypos = 18;
        addSlot(input_selection_slot=(new StorageSlot(block_inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // output selection storage slot
      {
        int xpos = 134, ypos = 108;
        addSlot(output_selection_slot=(new StorageSlot(block_inventory_, ++i, xpos, ypos).setSlotStackLimit(1)));
      }
      // result storage slot
      {
        int xpos = 92, ypos = 50;
        addSlot(result_slot = new Containers.LockedSlot(block_inventory_, ++i, xpos, ypos));
        result_slot.enabled = false;
      }
      storage_slot_count = i+1;
      // block slot fillup for stack synchronisation
      {
        while(i< block_inventory_.getContainerSize()-1) addSlot(new Containers.HiddenSlot(block_inventory_, ++i));
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
      this.addDataSlots(fields_);
    }

    // -----------------------------------------------------------------------------------------

    public final int field(int index)
    { return fields_.get(index); }

    private void sync()
    {
      block_inventory_.setChanged();
      player_.getInventory().setChanged();
      broadcastChanges();
    }

    // Container -------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player)
    { return block_inventory_.stillValid(player); }

    @Override
    public void removed(Player player)
    {
      super.removed(player);
      block_inventory_.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
      final int player_start_index = block_inventory_.getContainerSize();
      Slot slot = getSlot(index);
      if(!slot.hasItem()) return ItemStack.EMPTY;
      ItemStack slot_stack = slot.getItem();
      ItemStack transferred = slot_stack.copy();
      if((index>=0) && (index<player_start_index)) {
        // Storage slots -> to player
        if(!moveItemStackTo(slot_stack, player_start_index, player_start_index+36, false)) return ItemStack.EMPTY;
      } else if((index >= player_start_index) && (index <= player_start_index+36)) {
        // Player slot -> to storage
        if(slot_stack.getItem() == ANTS_ITEM) {
          if(!moveItemStackTo(slot_stack, RedAntHiveTileEntity.ANT_STORAGE_START, RedAntHiveTileEntity.ANT_STORAGE_START+RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS, false)) {
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
        slot.set(ItemStack.EMPTY);
      } else {
        slot.setChanged();
      }
      if(slot_stack.getCount() == transferred.getCount()) return ItemStack.EMPTY;
      slot.onTake(player, slot_stack);
      return transferred;
    }

    @Override
    public void clicked(int slot, int dragType, ClickType clickType, Player player)
    {
      if((te_ == null)
      || (slot < 0) || (slot >= storage_slot_count)
      || (dragType != 0)
      || (clickType != ClickType.PICKUP)
      || (getSlot(slot).hasItem()) // <-- before super.slotClick()
      || (!te_.getStateFlags().filteredinsert())
      ) {
        super.clicked(slot, dragType, clickType, player);
        return;
      }
      final int index = getSlot(slot).getSlotIndex() - left_storage_slot_range_.offset();
      if((index < 0) || (index >= left_storage_slot_range_.size())) {
        super.clicked(slot, dragType, clickType, player);
      } else {
        super.clicked(slot, dragType, clickType, player);
        if(!getSlot(slot).hasItem()) { // <-- after super.slotClick()
          // Was just clicked, no item transfer -> clear
          left_filter_slot_range_.setItem(index, ItemStack.EMPTY);
          sync();
        }
      }
    }

    // INetworkSynchronisableContainer ---------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(CompoundTag nbt)
    { Networking.PacketContainerSyncClientToServer.sendToServer(containerId, nbt); }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String key, int value)
    {
      CompoundTag nbt = new CompoundTag();
      nbt.putInt(key, value);
      Networking.PacketContainerSyncClientToServer.sendToServer(containerId, nbt);
    }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message)
    {
      CompoundTag nbt = new CompoundTag();
      nbt.putString("action", message);
      Networking.PacketContainerSyncClientToServer.sendToServer(containerId, nbt);
    }

    @OnlyIn(Dist.CLIENT)
    public void onGuiAction(String message, CompoundTag nbt)
    {
      nbt.putString("action", message);
      Networking.PacketContainerSyncClientToServer.sendToServer(containerId, nbt);
    }

    @Override
    public void onServerPacketReceived(int windowId, CompoundTag nbt)
    {}

    @Override
    public void onClientPacketReceived(int windowId, Player player, CompoundTag nbt)
    {
      if(!nbt.contains("action")) return;
      boolean changed = false;
      final int slotId = nbt.contains("slot") ? nbt.getInt("slot") : -1;
      switch(nbt.getString("action")) {
        case "input-filter-on":  { te_.enableInsertionFilter(true);  changed = true; break; }
        case "input-filter-off": { te_.enableInsertionFilter(false); changed = true; break; }
        case "pass-through-on":  { te_.getStateFlags().nopassthrough(false); changed = true; break; }
        case "pass-through-off": { te_.getStateFlags().nopassthrough(true); changed = true; break; }
        default: break;
      }
      if(changed) {
        block_inventory_.setChanged();
        player.getInventory().setChanged();
        broadcastChanges();
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // GUI
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class RedAntHiveGui extends ContainerGui<RedAntHiveMenu>
  {
    protected final Guis.BackgroundImage gui_background_;
    protected final Guis.BackgroundImage grid_background_;
    protected final Guis.BackgroundImage result_background_;
    protected final Guis.BackgroundImage powered_indicator_;
    protected final Guis.BackgroundImage sugar_indicator_;
    protected final Guis.BackgroundImage norecipe_indicator_;
    protected final Guis.BackgroundImage noingredients_indicator_;
    protected final Guis.BackgroundImage nofuel_indicator_;
    protected final Guis.BackgroundImage noants_indicator_;
    protected final Guis.HorizontalProgressBar progress_bar_;
    protected final Guis.CheckBox left_filter_enable_;
    protected final Guis.CheckBox passthrough_enable_;
    protected final TooltipDisplay tooltip_ = new TooltipDisplay();
    protected final Player player_;
    protected final RedAntHiveTileEntity.StateFlags state_flags_ = new RedAntHiveTileEntity.StateFlags(0);
    protected final Component EMPTY_TOOLTIP = new TextComponent("");
    protected final List<Item> command_items_with_process_bar = new ArrayList<>();
    protected final List<Item> command_items_grid_visible = new ArrayList<>();
    protected final List<Item> command_items_result_visible = new ArrayList<>();

    public RedAntHiveGui(RedAntHiveMenu container, Inventory player_inventory, Component title)
    {
      super(container, player_inventory, title, new ResourceLocation(ModAnthillInside.MODID, "textures/gui/hive_gui.png"));
      player_ = player_inventory.player;
      imageWidth = 176;
      imageHeight = 222;
      gui_background_    = new Guis.BackgroundImage(background_image, imageWidth,imageHeight, new Coord2d(0,0));
      grid_background_   = new Guis.BackgroundImage(background_image, 54,54, new Coord2d(180,71));
      result_background_ = new Guis.BackgroundImage(background_image, 22,18, new Coord2d(206,49));
      progress_bar_      = new HorizontalProgressBar(background_image, 40,8, new Coord2d(180,0), new Coord2d(180,8));
      powered_indicator_ = new Guis.BackgroundImage(background_image, 9,8, new Coord2d(181,35));
      sugar_indicator_   = new Guis.BackgroundImage(background_image, 12,12, new Coord2d(230,19));
      noants_indicator_  = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(228,32));
      norecipe_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(196,17));
      noingredients_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(212,17));
      nofuel_indicator_ = new Guis.BackgroundImage(background_image, 16,16, new Coord2d(180,17));
      left_filter_enable_ = (new Guis.CheckBox(background_image, 5,6, new Coord2d(182,46), new Coord2d(182,53))).onclick((box)->container.onGuiAction(box.checked() ? "input-filter-on" : "input-filter-off"));
      passthrough_enable_ = (new Guis.CheckBox(background_image, 11,6, new Coord2d(189,46), new Coord2d(189,53))).onclick((box)->container.onGuiAction(box.checked() ? "pass-through-on" : "pass-through-off"));
      command_items_with_process_bar.add(Items.CRAFTING_TABLE);
      command_items_with_process_bar.add(Items.FURNACE);
      command_items_with_process_bar.add(Items.BLAST_FURNACE);
      command_items_with_process_bar.add(Items.SMOKER);
      command_items_with_process_bar.add(Items.COMPOSTER);
      command_items_with_process_bar.add(Items.BREWING_STAND);
      command_items_with_process_bar.add(Items.WHEAT);
      command_items_with_process_bar.add(Items.WHEAT_SEEDS);
      command_items_with_process_bar.add(Items.CARROT);
      command_items_with_process_bar.add(Items.STONE_HOE);
      command_items_with_process_bar.add(Items.IRON_HOE);
      command_items_with_process_bar.add(Items.DIAMOND_HOE);
      command_items_with_process_bar.add(Items.NETHERITE_HOE);
      command_items_with_process_bar.add(Items.GRINDSTONE);
      command_items_result_visible.add(Items.CRAFTING_TABLE);
      command_items_result_visible.add(Items.FURNACE);
      command_items_result_visible.add(Items.BLAST_FURNACE);
      command_items_result_visible.add(Items.SMOKER);
      command_items_result_visible.add(Items.COMPOSTER);
      command_items_result_visible.add(Items.BREWING_STAND);
      command_items_result_visible.add(Items.GRINDSTONE);
      command_items_grid_visible.add(Items.CRAFTING_TABLE);
      command_items_grid_visible.add(Items.FURNACE);
      command_items_grid_visible.add(Items.BLAST_FURNACE);
      command_items_grid_visible.add(Items.SMOKER);
    }

    private void update()
    {
      final RedAntHiveMenu container = getMenu();
      state_flags_.value(container.field(0));
      final ItemStack cmdstack = container.command_slot.getItem();
      final boolean show_process = (!cmdstack.isEmpty()) && (command_items_with_process_bar.contains(cmdstack.getItem()));
      grid_background_.visible = (show_process && (command_items_grid_visible.contains(cmdstack.getItem())) || (!container.grid_storage_slot_range_.isEmpty()));
      container.grid_slots.forEach(slot->{slot.enabled = grid_background_.visible;});
      progress_bar_.visible = show_process;
      progress_bar_.active = progress_bar_.visible;
      result_background_.visible = show_process && (command_items_result_visible.contains(cmdstack.getItem()));
      if(progress_bar_.visible) progress_bar_.setMaxProgress(container.field(1)).setProgress(container.field(2));
      powered_indicator_.visible = state_flags_.powered();
      sugar_indicator_.visible = state_flags_.sugared();
      noants_indicator_.visible = state_flags_.noants();
      norecipe_indicator_.visible = state_flags_.norecipe() && (!noants_indicator_.visible);
      noingredients_indicator_.visible = state_flags_.noingr() && (!noants_indicator_.visible);
      nofuel_indicator_.visible = state_flags_.nofuel() && (!noants_indicator_.visible);
      left_filter_enable_.checked(state_flags_.filteredinsert());
      passthrough_enable_.checked(!state_flags_.nopassthrough());
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
      noants_indicator_.init(this, new Coord2d(92,50)).hide();
      addRenderableWidget(progress_bar_.init(this, new Coord2d(69, 38)));
      addRenderableWidget(left_filter_enable_.init(this, new Coord2d(20, 126)));
      addRenderableWidget(passthrough_enable_.init(this, new Coord2d(28, 126)));
      left_filter_enable_.checked(state_flags_.filteredinsert());
      passthrough_enable_.checked(!state_flags_.nopassthrough());
      final String prefix = ModContent.HIVE_BLOCK.getDescriptionId() + ".tooltips.";
      final int x0 = getGuiLeft(), y0 = getGuiTop();
      tooltip_.init(
        new TooltipDisplay.TipRange(
          x0+164,y0+6, 7,9,
          ()->(new TranslatableComponent(prefix + "help"))
        ),
        new TooltipDisplay.TipRange(
          powered_indicator_.x, powered_indicator_.y, powered_indicator_.getWidth(), powered_indicator_.getHeight(),
          ()->(powered_indicator_.visible ? new TranslatableComponent(prefix + "powered") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          sugar_indicator_.x, sugar_indicator_.y, sugar_indicator_.getWidth(), sugar_indicator_.getHeight(),
          ()->(sugar_indicator_.visible ? new TranslatableComponent(prefix + "sugartrip") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          norecipe_indicator_.x, norecipe_indicator_.y, norecipe_indicator_.getWidth(), norecipe_indicator_.getHeight(),
          ()->(norecipe_indicator_.visible ? new TranslatableComponent(prefix + "norecipe") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          noingredients_indicator_.x, noingredients_indicator_.y, noingredients_indicator_.getWidth(), noingredients_indicator_.getHeight(),
          ()->(noingredients_indicator_.visible ? new TranslatableComponent(prefix + "noingredients") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          nofuel_indicator_.x, nofuel_indicator_.y, nofuel_indicator_.getWidth(), nofuel_indicator_.getHeight(),
          ()->(nofuel_indicator_.visible ? new TranslatableComponent(prefix + "nofuel") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          noants_indicator_.x, noants_indicator_.y, noants_indicator_.getWidth(), noants_indicator_.getHeight(),
          ()->(noants_indicator_.visible ? new TranslatableComponent(prefix + "noants") : EMPTY_TOOLTIP)
        ),
        new TooltipDisplay.TipRange(
          x0+12, y0+22, 8,8,
          ()->(new TranslatableComponent(prefix + "inputside"))
        ),
        new TooltipDisplay.TipRange(
          x0+156, y0+112, 8,8,
          ()->(new TranslatableComponent(prefix + "outputside"))
        ),
        new TooltipDisplay.TipRange(
          x0+28, y0+126, 11,6,
          ()->(new TranslatableComponent(prefix + "passthroughlock"))
        ),
        new TooltipDisplay.TipRange(
          x0+20, y0+126, 5,6,
          ()->(new TranslatableComponent(prefix + "insertionlock"))
        ),
        new TooltipDisplay.TipRange(
          x0+26, y0+18, 16,16,
          ()->(getMenu().input_selection_slot.hasItem() ? EMPTY_TOOLTIP : new TranslatableComponent(prefix + "inputselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+134, y0+108, 16,16,
          ()->(getMenu().output_selection_slot.hasItem() ? EMPTY_TOOLTIP : new TranslatableComponent(prefix + "outputselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+70, y0+50, 16,16,
          ()->(getMenu().command_slot.hasItem() ? EMPTY_TOOLTIP : new TranslatableComponent(prefix + "workselect"))
        ),
        new TooltipDisplay.TipRange(
          x0+62, y0+18, 52,16,
          ()->(getMenu().ant_slot_range_.isEmpty() ? new TranslatableComponent(prefix + "antslots") : EMPTY_TOOLTIP )
        )
      ).delay(400);
      update();
    }

    @Override
    public void containerTick()
    { super.containerTick(); update(); }

    @Override
    public void render(PoseStack mx, int mouseX, int mouseY, float partialTicks)
    {
      renderBackground(mx);
      super.render(mx, mouseX, mouseY, partialTicks);
      if(!tooltip_.render(mx, this, mouseX, mouseY)) renderTooltip(mx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(PoseStack mx, int x, int y)
    {
      font.draw(mx, title, (float)titleLabelX+1, (float)titleLabelY+1, 0x303030);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type)
    {
      tooltip_.resetTimer();
      super.slotClicked(slot, slotId, button, type);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
    {
      tooltip_.resetTimer();
      return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void renderBg(PoseStack mx, float partialTicks, int mouseX, int mouseY)
    {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      final int x0=getGuiLeft(), y0=getGuiTop(), w=getXSize(), h=getYSize();
      this.blit(mx, x0, y0, 0, 0, w, h);
      final RedAntHiveMenu container = getMenu();
      // backgrounds images
      {
        gui_background_.draw(mx, this);
        grid_background_.draw(mx,this);
        result_background_.draw(mx,this);
        powered_indicator_.draw(mx,this);
        sugar_indicator_.draw(mx,this);
        norecipe_indicator_.draw(mx,this);
        noants_indicator_.draw(mx,this);
        noingredients_indicator_.draw(mx,this);
        nofuel_indicator_.draw(mx,this);
      }
      // result slot
      {
        final ItemStack stack = container.result_slot.getItem();
        if(!stack.isEmpty()) renderItemTemplate(mx, stack, container.result_slot.x , container.result_slot.y);
      }
      // ants background
      {
        for(int i=0; i<RedAntHiveTileEntity.ANT_STORAGE_NUM_SLOTS; ++i) {
          final Slot slot = container.getSlot(RedAntHiveTileEntity.ANT_STORAGE_START+i);
          if(slot.getItem().isEmpty()) {
            renderItemTemplate(mx, new ItemStack(ANTS_ITEM), slot.x , slot.y);
          }
        }
      }
      // left filter slot background
      {
        if(state_flags_.filteredinsert()) {
          for(int i=0; i<RedAntHiveTileEntity.LEFT_STORAGE_NUM_SLOTS; ++i) {
            final Slot slot = container.getSlot(RedAntHiveTileEntity.LEFT_STORAGE_START+i);
            if(slot.getItem().isEmpty()) {
              final ItemStack filter_stack = container.left_filter_slot_range_.getItem(i);
              if(!filter_stack.isEmpty()) {
                renderItemTemplate(mx, filter_stack, slot.x , slot.y);
              }
            }
          }
        }
      }
      RenderSystem.disableBlend();
    }
  }

}
