package wile.anthillinside.libmc.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BiConsumer;

public class Containers
{
  // -------------------------------------------------------------------------------------------------------------------
  // Slots
  // -------------------------------------------------------------------------------------------------------------------

  public static class StorageSlot extends Slot
  {
    protected BiConsumer<ItemStack, ItemStack> slot_change_action_ = (oldStack, newStack)->{};
    protected int stack_limit_ = 64;
    public boolean enabled = true;

    public StorageSlot(IInventory inventory, int index, int x, int y)
    { super(inventory, index, x, y); }

    public StorageSlot setSlotStackLimit(int limit)
    { stack_limit_ = MathHelper.clamp(limit, 1, 64); return this; }

    public int getSlotStackLimit()
    { return stack_limit_; }

    public StorageSlot setSlotChangeNotifier(BiConsumer<ItemStack, ItemStack> action)
    { slot_change_action_ = action; return this; }

    @Override
    public void onSlotChange(ItemStack oldStack, ItemStack newStack)
    { slot_change_action_.accept(oldStack, newStack);} // no crafting trigger

    @Override
    public boolean isItemValid(ItemStack stack)
    { return enabled && this.inventory.isItemValidForSlot(this.getSlotIndex(), stack); }

    @Override
    public int getItemStackLimit(ItemStack stack)
    { return Math.min(getSlotStackLimit(), stack_limit_); }

    @OnlyIn(Dist.CLIENT)
    public boolean isEnabled()
    { return enabled; }
  }

  public static class LockedSlot extends Slot
  {
    protected int stack_limit_ = 64;
    public boolean enabled = true;

    public LockedSlot(IInventory inventory, int index, int x, int y)
    { super(inventory, index, x, y); }

    public LockedSlot setSlotStackLimit(int limit)
    { stack_limit_ = MathHelper.clamp(limit, 1, 64); return this; }

    public int getSlotStackLimit()
    { return stack_limit_; }

    @Override
    public int getItemStackLimit(ItemStack stack)
    { return Math.min(getSlotStackLimit(), stack_limit_); }

    @Override
    public boolean isItemValid(ItemStack stack)
    { return false; }

    @Override
    public boolean canTakeStack(PlayerEntity player)
    { return false; }

    @OnlyIn(Dist.CLIENT)
    public boolean isEnabled()
    { return enabled; }
  }

  public static class HiddenSlot extends Slot
  {
    public HiddenSlot(IInventory inventory, int index)
    { super(inventory, index, 0, 0); }

    @Override
    public int getItemStackLimit(ItemStack stack)
    { return getSlotStackLimit(); }

    @Override
    public boolean isItemValid(ItemStack stack)
    { return false; }

    @Override
    public boolean canTakeStack(PlayerEntity player)
    { return false; }

    @OnlyIn(Dist.CLIENT)
    public boolean isEnabled()
    { return false; }
  }

}
