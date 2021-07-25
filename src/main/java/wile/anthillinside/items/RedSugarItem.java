/*
 * @file RedSugarItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;


public class RedSugarItem extends BaseItem
{
  public RedSugarItem(Item.Properties properties)
  { super(properties.setNoRepair()); }

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
}
