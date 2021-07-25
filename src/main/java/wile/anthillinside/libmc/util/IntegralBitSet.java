package wile.anthillinside.libmc.util;

public class IntegralBitSet
{
  private long value_ = 0;

  public IntegralBitSet()
  {}

  public IntegralBitSet(long initial)
  { value_ = initial; }

  public IntegralBitSet(int initial)
  { value_ = initial; }

  public long mask(long mask)
  { return (value_ & mask); }

  public IntegralBitSet mask(long mask, long value)
  { value_ = (value_ & ~mask) | value; return this; }

  public boolean bit(int at)
  { return (value_ & (1L<<at)) != 0; }

  public IntegralBitSet bit(int at, boolean val)
  { if(!val) { value_ &= ~(1L<<at); } else { value_ |= (1L<<at); } return this; }

  public long value()
  { return value_; }

  public IntegralBitSet value(long val)
  { value_ = val; return this; }

  public int int_value()
  { return (int)(value_ & 0xffffffffL); }
}
