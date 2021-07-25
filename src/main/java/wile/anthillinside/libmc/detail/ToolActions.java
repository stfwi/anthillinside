/*
 * @file ToolActions.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General actions which can be done with tools.
 */
package wile.anthillinside.libmc.detail;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;


public class ToolActions
{
  public static boolean shearEntity(LivingEntity entity)
  {
    if((entity.level.isClientSide()) || (!(entity instanceof net.minecraftforge.common.IForgeShearable target))) return false;
    final BlockPos pos = new BlockPos(entity.blockPosition());
    final ItemStack tool = new ItemStack(Items.SHEARS);
    if(target.isShearable(tool, entity.level, pos)) {
      List<ItemStack> drops = target.onSheared(null, tool, entity.level, pos, 1);
      Random rand = new java.util.Random();
      drops.forEach(d -> {
        ItemEntity ent = entity.spawnAtLocation(d, 1f);
        if(ent == null) return;
        ent.setDeltaMovement(ent.getDeltaMovement().add(((rand.nextFloat() - rand.nextFloat()) * 0.05f), (rand.nextFloat() * 0.05f), ((rand.nextFloat() - rand.nextFloat()) * 0.05f)));
      });
      entity.level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

  public static boolean shearEntities(Level world, AABB range, int max_count)
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

  public static Optional<List<ItemStack>> harvestCrop(ServerLevel world, BlockPos pos, boolean try_replant, ItemStack bone_meal)
  {
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    if(!(block instanceof final CropBlock crop)) return Optional.empty();
    if(!crop.isMaxAge(state)) {
      if((!bone_meal.isEmpty()) && (crop.isBonemealSuccess(world, world.getRandom(), pos, state))) {
        bone_meal.shrink(1);
        crop.performBonemeal(world, world.getRandom(), pos, state);
      }
      return Optional.of(Collections.emptyList());
    } else {
      final List<ItemStack> drops = state.getDrops(
        (new LootContext.Builder(world))
          .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
          .withParameter(LootContextParams.BLOCK_STATE, state)
          .withOptionalParameter(LootContextParams.BLOCK_ENTITY, world.getBlockEntity(pos))
          .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
          .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
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
            int init_val = crop.defaultBlockState().getValue(crop.getAgeProperty());
            world.setBlock(pos, crop.getStateForAge(init_val),1|2);
          }
        }
      }
      return Optional.of(drops);
    }
  }
}
