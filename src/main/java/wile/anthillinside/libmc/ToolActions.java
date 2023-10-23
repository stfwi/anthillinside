/*
 * @file ToolActions.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General actions which can be done with tools.
 */
package wile.anthillinside.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class ToolActions
{
  public static class Shearing
  {
    public static boolean shearEntity(LivingEntity entity)
    {
      if((entity.level().isClientSide()) || (!(entity instanceof Shearable target))) return false;
      if(!target.readyForShearing()) return false;
      final BlockPos pos = new BlockPos(entity.blockPosition());
      final ItemStack tool = new ItemStack(Items.SHEARS);
      target.shear(SoundSource.BLOCKS);
      return true;
    }

    public static boolean shearEntities(Level world, AABB range, int max_count)
    {
      if(max_count <= 0) return false;
      List<LivingEntity> entities = world.getEntitiesOfClass(LivingEntity.class, range, e->(e instanceof Shearable));
      boolean snipped = false;
      for(LivingEntity e:entities) {
        if(!shearEntity(e)) continue;
        snipped = true;
        if(--max_count == 0) break;
      }
      return snipped;
    }

    public static boolean shearPlant(Level world, BlockPos pos)
    {
      final BlockState state = world.getBlockState(pos);
      final Block block = state.getBlock();
      // replace with tag?
      if((!state.is(BlockTags.LEAVES)) && (block != Blocks.COBWEB) && (block != Blocks.GRASS) & (block != Blocks.TALL_GRASS)
              && (block != Blocks.FERN) && (block != Blocks.DEAD_BUSH) && (block != Blocks.VINE) && (block != Blocks.TRIPWIRE)
              && (!state.is(BlockTags.WOOL))
      ) return false;
      ItemEntity ie = new ItemEntity(world, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(block.asItem()));
      ie.setDefaultPickUpDelay();
      world.addFreshEntity(ie);
      world.setBlock(pos, Blocks.AIR.defaultBlockState(), 1|2|8);
      if(!world.isClientSide()) {
        world.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.1f);
      }
      return true;
    }
  }

  public static class Farming
  {
    public static boolean isPlantable(Block block)
    {
      // @todo: That was: (block instanceof IPlantable) && (!(block instanceof StemBlock)),
      // Also check MushroomBlock?
      return (block instanceof CropBlock) || (block instanceof SaplingBlock);
    }

    public static Optional<List<ItemStack>> harvestCrop(ServerLevel world, BlockPos pos, boolean try_replant, ItemStack bone_meal)
    {
      final BlockState state = world.getBlockState(pos);
      final Block block = state.getBlock();
      if(!(block instanceof final CropBlock crop)) return Optional.empty();
      if(!crop.isMaxAge(state)) {
        if((!bone_meal.isEmpty()) && fertilizePlant(world, pos, bone_meal, true, false)) bone_meal.shrink(1);
        return Optional.of(Collections.emptyList());
      } else {
        final List<ItemStack> drops = state.getDrops(
                (new LootParams.Builder(world))
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.BLOCK_STATE, state)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, world.getBlockEntity(pos))
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
                        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
        );
        world.destroyBlock(pos, false);
        if(try_replant) {
          final ItemStack seed = crop.getCloneItemStack(world, pos, state);
          if(!seed.isEmpty()) {
            boolean fetched_seed = false;
            for(ItemStack stack:drops) {
              if(stack.is(seed.getItem())) {
                stack.shrink(1);
                fetched_seed = true;
                break;
              }
            }
            if(fetched_seed) {
              final int init_val = crop.getAge(crop.defaultBlockState());
              world.setBlock(pos, crop.getStateForAge(init_val),1|2);
            }
          }
        }
        return Optional.of(drops);
      }
    }

    public static Optional<List<ItemStack>> harvestPlant(ServerLevel world, BlockPos pos, boolean try_replant, ItemStack bone_meal)
    {
      return harvestCrop(world, pos, try_replant, bone_meal).or(()-> {
        final BlockState state = world.getBlockState(pos);
        if(state.isAir()) return Optional.empty();
        final Block block = state.getBlock();
        final Supplier<List<ItemStack>> dropgen = ()->state.getDrops(
                (new LootParams.Builder(world))
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.BLOCK_STATE, state)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, world.getBlockEntity(pos))
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
                        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
        );
        if(block instanceof StemGrownBlock) {
          // Pumpkin/Melon
          final List<ItemStack> drops = dropgen.get();
          if(!drops.isEmpty()) {
            // Could be broken with drops without a tool.
            world.destroyBlock(pos, false);
            return Optional.of(drops);
          }
        } else if(block == Blocks.SUGAR_CANE) {
          // Special case Sugar Cane
          BlockPos p = pos;
          int n_harvested = 0;
          while(world.getBlockState(p.above()).getBlock() == block) {
            p = p.above();
          }
          while(world.getBlockState(p.below()).getBlock() == block) {
            ++n_harvested;
            world.destroyBlock(p, false);
            p = p.below();
          }
          if(n_harvested == 0) {
            return Optional.empty();
          } else {
            return Optional.of(Collections.singletonList(new ItemStack(Blocks.SUGAR_CANE.asItem(), n_harvested)));
          }
        } else if(isPlantable(block)) {
          // Plantable blocks with age @todo: Note to self, this one is expensive, check the property bla can be circumvented.
          final Property<?> ager = state.getProperties().stream().filter(p -> p.getName().equals("age")).findFirst().orElse(null);
          if(ager instanceof IntegerProperty age) {
            if(state.getValue(age) >= Collections.max(age.getPossibleValues())) {
              final List<ItemStack> drops = dropgen.get();
              if(drops.isEmpty()) {
                return Optional.empty();
              } else {
                world.destroyBlock(pos, false);
                if(try_replant) {
                  final ItemStack seed = block.getCloneItemStack(world, pos, state);
                  if(!seed.isEmpty()) {
                    final ItemStack seed_dropped = drops.stream().filter((stack)->stack.is(seed.getItem())).findFirst().orElse(ItemStack.EMPTY);
                    if(!seed_dropped.isEmpty()) {
                      seed_dropped.shrink(1);
                      world.setBlock(pos, state.setValue(age, 0),1|2);
                    }
                  }
                }
                return Optional.of(drops);
              }
            }
          }
        }
        if((!bone_meal.isEmpty()) && (block instanceof BonemealableBlock)) {
          bone_meal.shrink(1);
          ((BonemealableBlock)block).performBonemeal(world, world.getRandom(), pos, state);
        }
        return Optional.empty();
      });
    }

    public static boolean fertilizePlant(ServerLevel world, BlockPos pos, ItemStack bone_meal, boolean always_succeed, boolean no_particles)
    {
      if(bone_meal.isEmpty() || (!bone_meal.is(Items.BONE_MEAL))) return false;
      final BlockState state = world.getBlockState(pos);
      if(!(state.getBlock() instanceof BonemealableBlock block)) return false;
      if(!block.isValidBonemealTarget(world, pos, state, false)) return false;
      if((!always_succeed) && (!block.isBonemealSuccess(world, world.getRandom(), pos, state))) return false;
      block.performBonemeal(world, world.getRandom(), pos, state);
      if(!no_particles) Auxiliaries.particles(world, pos, ParticleTypes.HAPPY_VILLAGER);
      return true;
    }
  }

  public static class BlockBreaking
  {
    @SuppressWarnings("deprecation")
    public static boolean isBreakable(BlockState state, BlockPos pos, Level world)
    {
      if((state.isAir()) || state.liquid()) return false;
      final Block block = state.getBlock();
      if( // Manual never-break-whatever-functions-return-blocks.
              (block==Blocks.BEDROCK) || (block==Blocks.FIRE) || (block==Blocks.END_PORTAL) || (block==Blocks.END_GATEWAY)
                      || (block==Blocks.END_PORTAL_FRAME) || (block==Blocks.NETHER_PORTAL) || (block==Blocks.BARRIER) || (block==Blocks.AIR)
      ) return false;
      float bh = state.getDestroySpeed(world, pos);
      return !((bh<0) || (bh>55));
    }

    public static void dropOnGround(ItemStack stack, BlockPos pos, Level world)
    {
      ItemEntity e = new ItemEntity(world,
              ((world.random.nextFloat()*0.1)+0.5) + pos.getX(),
              ((world.random.nextFloat()*0.1)+0.5) + pos.getY(),
              ((world.random.nextFloat()*0.1)+0.5) + pos.getZ(),
              stack
      );
      e.setDefaultPickUpDelay();
      e.setDeltaMovement((world.random.nextFloat()*0.1-0.05), (world.random.nextFloat()*0.1-0.03), (world.random.nextFloat()*0.1-0.05));
      world.addFreshEntity(e);
    }

    public static List<ItemStack> blockDrops(BlockState state, BlockPos pos, ServerLevel world, @Nullable ItemStack tool)
    {
      if(!world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return Collections.emptyList();
      if(tool != null) {
        if(!(tool.getItem() instanceof DiggerItem pick)) return Collections.emptyList();
        final boolean candrop = pick.isCorrectToolForDrops(state);
        if(!candrop) return Collections.emptyList();
      }
      return Block.getDrops(state, world, pos, world.getBlockEntity(pos));
    }

    public static Optional<List<ItemStack>> breakBlock(BlockState state, BlockPos pos, Level world, @Nullable ItemStack tool, boolean dropYield)
    {
      if(world.isClientSide  || (!(world instanceof ServerLevel))) return Optional.empty(); // retry next cycle
      if(!isBreakable(state, pos, world)) return Optional.empty();
      final List<ItemStack> drops = blockDrops(state, pos, (ServerLevel)world, tool);
      world.destroyBlock(pos, false);
      if(!dropYield) return Optional.of(drops);
      for(ItemStack drop:drops) dropOnGround(drop, pos, world);
      return Optional.of(Collections.emptyList());
    }

    public static int breakTime(BlockState state, BlockPos pos, Level world, @Nullable ItemStack tool, int minTime, int maxTime, float reluctance)
    {
      minTime = Math.max(0, minTime);
      maxTime = Math.max(minTime, maxTime);
      final float speedup = (tool.getItem() instanceof DiggerItem pick) ? Mth.clamp(pick.getDestroySpeed(tool, state), 0.1f, 10f) : 1f;
      final float destroy_time = state.getDestroySpeed(world, pos) * 20;
      if(destroy_time <= 0) return minTime;
      return Mth.clamp((int)(destroy_time * reluctance/speedup), minTime, maxTime);
    }
  }
}
