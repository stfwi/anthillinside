/*
 * @file ToolActions.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General actions which can be done with tools.
 */
package wile.anthillinside.libmc.detail;

import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.*;


public class ToolActions
{
  public static boolean shearEntity(LivingEntity entity)
  {
    if((entity.level.isClientSide()) || (!(entity instanceof net.minecraftforge.common.IForgeShearable))) return false;
    net.minecraftforge.common.IForgeShearable target = (net.minecraftforge.common.IForgeShearable)entity;
    final BlockPos pos = new BlockPos(entity.blockPosition());
    final ItemStack tool = new ItemStack(Items.SHEARS);
    if(target.isShearable(tool, entity.level, pos)) {
      List<ItemStack> drops = target.onSheared(null, tool, entity.level, pos, 1);
      Random rand = new java.util.Random();
      drops.forEach(d -> {
        ItemEntity ent = entity.spawnAtLocation(d, 1f);
        if(ent == null) return;
        ent.setDeltaMovement(ent.getDeltaMovement().add((double)((rand.nextFloat() - rand.nextFloat()) * 0.05f), (double)(rand.nextFloat() * 0.05f), (double)((rand.nextFloat() - rand.nextFloat()) * 0.05f)));
      });
      entity.level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

  public static boolean shearEntities(World world, AxisAlignedBB range, int max_count)
  {
    if(max_count <= 0) return false;
    List<LivingEntity> entities = world.getEntitiesOfClass(LivingEntity.class, range, e->(e instanceof net.minecraftforge.common.IForgeShearable));
    boolean snipped = false;
    for(LivingEntity e:entities) {
      if(!shearEntity(e)) continue;
      snipped = true;
      if(--max_count == 0) break;
    }
    return snipped;
  }

  public static boolean shearPlant(World world, BlockPos pos)
  {
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    // replace with tag?
    if((!block.is(BlockTags.LEAVES)) && (block != Blocks.COBWEB) && (block != Blocks.GRASS) & (block != Blocks.TALL_GRASS)
      && (block != Blocks.FERN) && (block != Blocks.DEAD_BUSH) && (block != Blocks.VINE) && (block != Blocks.TRIPWIRE)
      && (!block.is(BlockTags.WOOL))
    ) return false;
    ItemEntity ie = new ItemEntity(world, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(block.asItem()));
    ie.setDefaultPickUpDelay();
    world.addFreshEntity(ie);
    world.setBlock(pos, Blocks.AIR.defaultBlockState(), 1|2|8);
    if(!world.isClientSide()) {
      world.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

  public static Optional<List<ItemStack>> harvestCrop(ServerWorld world, BlockPos pos, boolean try_replant, ItemStack bonemeal)
  {
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    if(!(block instanceof CropsBlock)) return Optional.empty();
    final CropsBlock crop = (CropsBlock)block;
    if(!crop.isMaxAge(state)) {
      if((!bonemeal.isEmpty()) && (crop.isBonemealSuccess(world, world.getRandom(), pos, state))) {
        bonemeal.shrink(1);
        crop.performBonemeal(world, world.getRandom(), pos, state);
      }
      return Optional.of(Collections.emptyList());
    } else {
      final List<ItemStack> drops = state.getDrops(
        (new LootContext.Builder(world))
          .withParameter(LootParameters.ORIGIN, Vector3d.atCenterOf(pos))
          .withParameter(LootParameters.BLOCK_STATE, state)
          .withOptionalParameter(LootParameters.BLOCK_ENTITY, world.getBlockEntity(pos))
          .withOptionalParameter(LootParameters.THIS_ENTITY, null)
          .withParameter(LootParameters.TOOL, ItemStack.EMPTY)
      );
      if(!try_replant) {
        world.removeBlock(pos, false);
      } else {
        final ItemStack seed = crop.getCloneItemStack(world, pos, state);
        if(!seed.isEmpty()) {
          boolean fetched_seed = false;
          for(ItemStack stack:drops) {
            if(stack.sameItem(seed)) {
              stack.shrink(1);
              fetched_seed = true;
              break;
            }
          }
          if(fetched_seed) {
            int initval = crop.defaultBlockState().getValue(crop.getAgeProperty());
            world.setBlock(pos, crop.getStateForAge(initval),1|2);
          }
        }
      }
      return Optional.of(drops);
    }
  }
}
