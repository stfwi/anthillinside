/*
 * @file RedSugarItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TransportedItemEntity extends ItemEntity
{
  @Nullable BlockPos target_position_ = null;

  @SuppressWarnings("all")
  public TransportedItemEntity(EntityType<? extends Entity> type, Level world)
  { super((EntityType<ItemEntity>)type, world); }

  public TransportedItemEntity(Level level, double d, double e, double f, ItemStack itemStack)
  { this(level, d, e, f, itemStack, level.random.nextDouble() * 0.2 - 0.1, 0.2, level.random.nextDouble() * 0.2 - 0.1); }

  public TransportedItemEntity(Level level, double d, double e, double f, ItemStack itemStack, double g, double h, double i)
  {
    this(EntityType.ITEM, level);
    setPos(d, e, f);
    setDeltaMovement(g, h, i);
    setItem(itemStack);
  }

  public static TransportedItemEntity of(ItemEntity entity)
  {
    TransportedItemEntity ie = new TransportedItemEntity(EntityType.ITEM, entity.level());
    ie.setItem(entity.getItem().copy());
    ie.copyPosition(entity);
    return ie;
  }

  public TransportedItemEntity setTargetPosition(@Nullable BlockPos pos)
  { target_position_ = pos; return this; }

  @Nullable public BlockPos getTargetPosition()
  { return target_position_; }

  @Override
  public void tick()
  {
    super.tick();
    if((getTargetPosition()==null) || isRemoved()) return;
    if(!onGround()) return;
    if(level().getRandom().nextDouble() > 0.3) return;
    if(getDeltaMovement().lengthSqr()>1e-2) return;
    Vec3 ds = getTargetPosition().getCenter().subtract(position()).normalize().scale(1e-2).add(0,8e-2,0);
    move(MoverType.SELF, ds);
  }

}
