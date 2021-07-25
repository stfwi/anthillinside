/*
 * @file BaseBlockItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.anthillinside.ModAnthillInside;
import wile.anthillinside.ModConfig;
import wile.anthillinside.libmc.detail.Auxiliaries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class BaseBlockItem extends BlockItem
{
  public BaseBlockItem(Block block, Item.Properties properties)
  { super(block, properties.tab(ModAnthillInside.ITEMGROUP)); }

  @Override
  @OnlyIn(Dist.CLIENT)
  public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag)
  { Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

  @Override
  public Collection<CreativeModeTab> getCreativeTabs()
  { return ModConfig.isOptedOut(this) ? (Collections.emptyList()) : (Collections.singletonList(ModAnthillInside.ITEMGROUP)); }

}
