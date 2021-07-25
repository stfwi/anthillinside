/*
 * @file BaseItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.anthillinside.ModAnthillInside;
import wile.anthillinside.ModConfig;
import wile.anthillinside.libmc.detail.Auxiliaries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class BaseItem extends Item
{
  public BaseItem(Item.Properties properties)
  { super(properties.tab(ModAnthillInside.ITEMGROUP)); }

  @Override
  @OnlyIn(Dist.CLIENT)
  public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag)
  { Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

  @Override
  public Collection<ItemGroup> getCreativeTabs()
  { return ModConfig.isOptedOut(this) ? (Collections.emptyList()) : (Collections.singletonList(ModAnthillInside.ITEMGROUP)); }

}
