/*
 * @file Recipes.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Recipe utility functionality.
 */
package wile.anthillinside.libmc.detail;

import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import wile.anthillinside.libmc.detail.Inventories.InventoryRange;

import javax.annotation.Nullable;
import java.util.*;


public class Crafting
{
  // -------------------------------------------------------------------------------------------------------------------

  public static final class CraftingGrid extends CraftingInventory
  {
    protected static final CraftingGrid instance3x3 = new CraftingGrid(3,3);

    protected CraftingGrid(int width, int height)
    { super(new Container(null,0) { public boolean canInteractWith(PlayerEntity player) { return false; } }, width, height); }

    protected void fill(IInventory grid)
    { for(int i=0; i<getSizeInventory(); ++i) setInventorySlotContents(i, i>=grid.getSizeInventory() ? ItemStack.EMPTY : grid.getStackInSlot(i)); }

    public List<ICraftingRecipe> getRecipes(World world, IInventory grid)
    { fill(grid); return world.getRecipeManager().getRecipes(IRecipeType.CRAFTING, this, world); }

    public List<ItemStack> getRemainingItems(World world, IInventory grid, ICraftingRecipe recipe)
    { fill(grid); return recipe.getRemainingItems(this); }

    public ItemStack getCraftingResult(World world, IInventory grid, ICraftingRecipe recipe)
    { fill(grid); return recipe.getCraftingResult(this); }
  }

  /**
   * Returns a Crafting recipe by registry name.
   */
  public static final Optional<ICraftingRecipe> getCraftingRecipe(World world, ResourceLocation recipe_id)
  {
    IRecipe<?> recipe = world.getRecipeManager().getRecipe(recipe_id).orElse(null);
    return (recipe instanceof ICraftingRecipe) ? Optional.of((ICraftingRecipe)recipe) : Optional.empty();
  }

  /**
   * Returns a list of matching recipes by the first N slots (crafting grid slots) of the given inventory.
   */
  public static final List<ICraftingRecipe> get3x3CraftingRecipes(World world, IInventory crafting_grid_slots)
  { return CraftingGrid.instance3x3.getRecipes(world, crafting_grid_slots); }

  /**
   * Returns a recipe by the first N slots (crafting grid slots).
   */
  public static final Optional<ICraftingRecipe> get3x3CraftingRecipe(World world, IInventory crafting_grid_slots)
  { return get3x3CraftingRecipes(world, crafting_grid_slots).stream().findFirst(); }

  /**
   * Returns the result item of the recipe with the given grid layout.
   */
  public static final ItemStack get3x3CraftingResult(World world, IInventory grid, ICraftingRecipe recipe)
  { return CraftingGrid.instance3x3.getCraftingResult(world, grid, recipe); }

  /**
   * Returns the items remaining in the grid after crafting 3x3.
   */
  public static final List<ItemStack> get3x3RemainingItems(World world, IInventory grid, ICraftingRecipe recipe)
  { return CraftingGrid.instance3x3.getRemainingItems(world, grid, recipe); }

  public static final List<ItemStack> get3x3Placement(World world, ICraftingRecipe recipe, IInventory item_inventory, @Nullable IInventory crafting_grid)
  {
    final int width = 3;
    final int height = 3;
    if(!recipe.canFit(width,height)) return Collections.emptyList();
    List<ItemStack> used = new ArrayList<>();   //NonNullList.withSize(width*height);
    for(int i=width*height; i>0; --i) used.add(ItemStack.EMPTY);
    IInventory check_inventory = Inventories.copyOf(item_inventory);
    InventoryRange source = new InventoryRange(check_inventory);
    final List<Ingredient> ingredients = recipe.getIngredients();
    final List<ItemStack> preferred = new ArrayList<>(width*height);
    if(crafting_grid != null) {
      for(int i=0; i<crafting_grid.getSizeInventory(); ++i) {
        ItemStack stack = crafting_grid.getStackInSlot(i);
        if(stack.isEmpty()) continue;
        stack = stack.copy();
        stack.setCount(1);
        if(!source.extract(stack).isEmpty()) preferred.add(stack);
      }
    }
    for(int i=0; i<ingredients.size(); ++i) {
      final Ingredient ingredient = ingredients.get(i);
      if(ingredient == Ingredient.EMPTY) continue;
      ItemStack stack = preferred.stream().filter(ingredient).findFirst().orElse(ItemStack.EMPTY);
      if(!stack.isEmpty()) {
        preferred.remove(stack);
      } else {
        stack = source.stream().filter(ingredient).findFirst().orElse(ItemStack.EMPTY);
        if(stack.isEmpty()) return Collections.emptyList();
        stack = stack.copy();
        stack.setCount(1);
        if(source.extract(stack).isEmpty()) return Collections.emptyList();
      }
      used.set(i, stack);
    }
    if(recipe instanceof ShapedRecipe) {
      List<ItemStack> placement = NonNullList.withSize(width*height, ItemStack.EMPTY);
      ShapedRecipe shaped = (ShapedRecipe)recipe;
      for(int row=0; row<shaped.getRecipeHeight(); ++row) {
        for(int col=0; col<shaped.getRecipeWidth(); ++col) {
          placement.set(width*row+col, used.get(row*shaped.getRecipeWidth()+col));
        }
      }
      return placement;
    } else {
      return used;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Returns the recipe for a given input stack to smelt, null if there is no recipe
   * for the given type (SMELTING,BLASTING,SMOKING, etc).
   */
  public static final <T extends IRecipe<?>> Optional<AbstractCookingRecipe> getFurnaceRecipe(IRecipeType<T> recipe_type, World world, ItemStack input_stack)
  {
    if(input_stack.isEmpty()) {
      return Optional.empty();
    } else if(recipe_type == IRecipeType.SMELTING) {
      Inventory inventory = new Inventory(3);
      inventory.setInventorySlotContents(0, input_stack);
      FurnaceRecipe recipe = world.getRecipeManager().getRecipe(IRecipeType.SMELTING, inventory, world).orElse(null);
      return (recipe==null) ? Optional.empty() : Optional.of(recipe);
    } else if(recipe_type == IRecipeType.BLASTING) {
      Inventory inventory = new Inventory(3);
      inventory.setInventorySlotContents(0, input_stack);
      BlastingRecipe recipe = world.getRecipeManager().getRecipe(IRecipeType.BLASTING, inventory, world).orElse(null);
      return (recipe==null) ? Optional.empty() : Optional.of(recipe);
    } else if(recipe_type == IRecipeType.SMOKING) {
      Inventory inventory = new Inventory(3);
      inventory.setInventorySlotContents(0, input_stack);
      SmokingRecipe recipe = world.getRecipeManager().getRecipe(IRecipeType.SMOKING, inventory, world).orElse(null);
      return (recipe==null) ? Optional.empty() : Optional.of(recipe);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns the burn time of an item when used as fuel, 0 if it is no fuel.
   */
  public static final int getFuelBurntime(World world, ItemStack stack)
  {
    if(stack.isEmpty()) return 0;
    int t = ForgeHooks.getBurnTime(stack);
    return (t<0) ? 0 : t;
  }

  /**
   * Returns true if an item can be used as fuel.
   */
  public static final boolean isFuel(World world, ItemStack stack)
  { return (getFuelBurntime(world, stack) > 0) || (stack.getItem()==Items.LAVA_BUCKET); }

  /**
   * Returns burntime and remaining stack then the item shall be used as fuel.
   */
  public static final Tuple<Integer,ItemStack> consumeFuel(World world, ItemStack stack)
  {
    if(stack.isEmpty()) return new Tuple<>(0,stack);
    int burnime = getFuelBurntime(world, stack);
    if((stack.getItem()==Items.LAVA_BUCKET)) {
      if(burnime <= 0) burnime = 1000*20;
      return new Tuple<>(burnime,new ItemStack(Items.BUCKET));
    } else if(burnime <= 0) {
      return new Tuple<>(0,stack);
    } else {
      ItemStack left_over = stack.copy();
      left_over.shrink(1);
      return new Tuple<>(burnime,left_over);
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Returns true if the item can be used as brewing fuel.
   */
  public static final boolean isBrewingFuel(World world, ItemStack stack)
  { return (stack.getItem() == Items.BLAZE_POWDER) || (stack.getItem() == Items.BLAZE_ROD); }

  /**
   * Returns true if the item can be used as brewing ingredient.
   */
  public static final boolean isBrewingIngredient(World world, ItemStack stack)
  { return BrewingRecipeRegistry.isValidIngredient(stack); }

  /**
   * Returns true if the item can be used as brewing bottle.
   */
  public static final boolean isBrewingInput(World world, ItemStack stack)
  { return BrewingRecipeRegistry.isValidInput(stack); }

  /**
   * Returns the burn time for brewing of the given stack.
   */
  public static final int getBrewingFuelBurntime(World world, ItemStack stack)
  {
    if(stack.isEmpty()) return 0;
    if(stack.getItem() == Items.BLAZE_POWDER) return (400*20);
    if(stack.getItem() == Items.BLAZE_ROD) return (400*40);
    return 0;
  }

  /**
   * Returns brewing burn time and remaining stack if the item shall be used as fuel.
   */
  public static final Tuple<Integer,ItemStack> consumeBrewingFuel(World world, ItemStack stack)
  {
    int burntime = getBrewingFuelBurntime(world, stack);
    if(burntime <= 0) return new Tuple<>(0, stack.copy());
    stack = stack.copy();
    stack.shrink(1);
    return new Tuple<>(burntime, stack.isEmpty() ? ItemStack.EMPTY : stack);
  }


  public static final class BrewingOutput
  {
    public static final int DEFAULT_BREWING_TIME = 400;
    public static final BrewingOutput EMPTY = new BrewingOutput(ItemStack.EMPTY, new Inventory(1), new Inventory(1), 0,0, DEFAULT_BREWING_TIME);
    public final ItemStack item;
    public final IInventory potionInventory;
    public final IInventory ingredientInventory;
    public final int potionSlot;
    public final int ingredientSlot;
    public final int brewTime;

    public BrewingOutput(ItemStack output_potion, IInventory potion_inventory, IInventory ingredient_inventory, int potion_slot, int  ingredient_slot, int time_needed)
    {
      item = output_potion;
      potionInventory = potion_inventory;
      ingredientInventory = ingredient_inventory;
      potionSlot = potion_slot;
      ingredientSlot = ingredient_slot;
      brewTime = time_needed;
    }

    public static BrewingOutput find(World world, IInventory potion_inventory, IInventory ingredient_inventory)
    {
      for(int potion_slot = 0; potion_slot<potion_inventory.getSizeInventory(); ++potion_slot) {
        final ItemStack pstack = potion_inventory.getStackInSlot(potion_slot);
        if(!isBrewingInput(world, pstack)) continue;
        for(int ingredient_slot = 0; ingredient_slot<ingredient_inventory.getSizeInventory(); ++ingredient_slot) {
          final ItemStack istack = ingredient_inventory.getStackInSlot(ingredient_slot);
          if((!isBrewingIngredient(world, istack)) || (ingredient_slot == potion_slot) || (isBrewingFuel(world, istack))) continue;
          final ItemStack result = BrewingRecipeRegistry.getOutput(pstack, istack);
          if(result.isEmpty()) continue;
          return new BrewingOutput(result, potion_inventory, ingredient_inventory, potion_slot, ingredient_slot, DEFAULT_BREWING_TIME);
        }
      }
      return BrewingOutput.EMPTY;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  public static final double getCompostingChance(ItemStack stack)
  { return ComposterBlock.CHANCES.getOrDefault(stack.getItem(),0); }

  // -------------------------------------------------------------------------------------------------------------------
}