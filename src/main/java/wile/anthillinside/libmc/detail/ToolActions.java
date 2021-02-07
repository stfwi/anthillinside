/*
 * @file ToolActions.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General actions which can be done with tools.
 */
package wile.anthillinside.libmc.detail;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;


public class ToolActions
{

  public static boolean shearEntity(LivingEntity entity)
  {
    if((entity.world.isRemote()) || (!(entity instanceof net.minecraftforge.common.IForgeShearable))) return false;
    net.minecraftforge.common.IForgeShearable target = (net.minecraftforge.common.IForgeShearable)entity;
    final BlockPos pos = new BlockPos(entity.getPosition());
    final ItemStack tool = new ItemStack(Items.SHEARS);
    if(target.isShearable(tool, entity.world, pos)) {
      List<ItemStack> drops = target.onSheared(null, tool, entity.world, pos, 1);
      Random rand = new java.util.Random();
      drops.forEach(d -> {
        ItemEntity ent = entity.entityDropItem(d, 1f);
        if(ent == null) return;
        ent.setMotion(ent.getMotion().add((double)((rand.nextFloat() - rand.nextFloat()) * 0.05f), (double)(rand.nextFloat() * 0.05f), (double)((rand.nextFloat() - rand.nextFloat()) * 0.05f)));
      });
      entity.world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

  public static boolean shearEntities(World world, AxisAlignedBB range, int max_count)
  {
    if(max_count <= 0) return false;
    List<LivingEntity> entities = world.getEntitiesWithinAABB(LivingEntity.class, range, e->(e instanceof net.minecraftforge.common.IForgeShearable));
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
    if((!block.isIn(BlockTags.LEAVES)) && (block != Blocks.COBWEB) && (block != Blocks.GRASS) & (block != Blocks.TALL_GRASS)
      && (block != Blocks.FERN) && (block != Blocks.DEAD_BUSH) && (block != Blocks.VINE) && (block != Blocks.TRIPWIRE)
      && (!block.isIn(BlockTags.WOOL))
    ) return false;
    ItemEntity ie = new ItemEntity(world, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(block.asItem()));
    ie.setDefaultPickupDelay();
    world.addEntity(ie);
    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 1|2|8);
    if(!world.isRemote()) {
      world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

}
