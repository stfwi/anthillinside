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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class AntsItem extends BaseBlockItem
{
  public AntsItem(Block trail, Item.Properties properties)
  { super(trail, properties.setNoRepair()); }

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
  public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
  { return false; }

  @Override
  @SuppressWarnings("deprecation")
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
  {
    if(context.getPlayer().isCrouching()) {
      final Level world = context.getLevel();
      final BlockPos pos = context.getClickedPos();
      final BlockState state = world.getBlockState(pos);
      if(!(state.getBlock() instanceof final wile.anthillinside.blocks.RedAntTrail.RedAntTrailBlock trail)) return InteractionResult.PASS;
      world.setBlock(pos, trail.updatedState(state.rotate(Rotation.CLOCKWISE_90), world, pos), 1|2);
      world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7f,1.8f);
      return InteractionResult.sidedSuccess(world.isClientSide);
    }
    return InteractionResult.PASS;
  }
}
