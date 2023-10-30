/*
 * @file AntsItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.items;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import wile.anthillinside.libmc.Registries;
import wile.anthillinside.libmc.StandardItems;

public class AntsItem extends StandardItems.BaseBlockItem
{
  public AntsItem(Item.Properties properties)
  { super(Registries.getBlock("trail"), properties); }

  public String getDescriptionId()
  { return getOrCreateDescriptionId(); }

  @Override
  public int getEnchantmentValue()
  { return 0; }

  @Override
  public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair)
  { return false; }

  @Override
  public boolean canBeDepleted()
  { return false; }

  @Override
  public boolean isEnchantable(ItemStack stack)
  { return false; }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
  {
    if(!context.getPlayer().isCrouching()) return InteractionResult.PASS;
    final Level world = context.getLevel();
    final BlockPos pos = context.getClickedPos();
    final BlockState state = world.getBlockState(pos);
    if(!(state.getBlock() instanceof final wile.anthillinside.blocks.RedAntTrail.RedAntTrailBlock trail)) return InteractionResult.PASS;
    world.setBlock(pos, trail.updatedState(state.rotate(Rotation.CLOCKWISE_90), world, pos), 1|2);
    world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7f,1.8f);
    return InteractionResult.sidedSuccess(world.isClientSide);
  }

  @Override
  public InteractionResult place(BlockPlaceContext context)
  { return super.place(context); }
}
