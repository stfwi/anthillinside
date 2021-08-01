/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.Logger;
import wile.anthillinside.blocks.*;
import wile.anthillinside.items.*;
import wile.anthillinside.libmc.blocks.StandardBlocks;
import wile.anthillinside.libmc.blocks.StandardBlocks.IStandardBlock;
import wile.anthillinside.libmc.detail.Auxiliaries;

import javax.annotation.Nonnull;
import java.util.*;

public class ModContent
{
  private static final Logger LOGGER = ModAnthillInside.LOGGER;
  private static final String MODID = ModAnthillInside.MODID;

  // -----------------------------------------------------------------------------------------------------------------
  // -- Blocks
  // -----------------------------------------------------------------------------------------------------------------

  public static final RedAntHive.RedAntHiveBlock HIVE_BLOCK = (RedAntHive.RedAntHiveBlock)(new RedAntHive.RedAntHiveBlock(
    StandardBlocks.CFG_CUTOUT|StandardBlocks.CFG_WATERLOGGABLE|StandardBlocks.CFG_LOOK_PLACEMENT,
    BlockBehaviour.Properties.of(Material.STONE, MaterialColor.STONE).strength(2f, 6f).sound(SoundType.STONE),
    new AABB[]{
      Auxiliaries.getPixeledAABB(1,1,0,15,15, 1),
      Auxiliaries.getPixeledAABB(0,0,1,16,16,16),
    }
  )).setRegistryName(new ResourceLocation(MODID, "hive"));

  public static final RedAntTrail.RedAntTrailBlock TRAIL_BLOCK = (RedAntTrail.RedAntTrailBlock)(new RedAntTrail.RedAntTrailBlock(
    StandardBlocks.CFG_TRANSLUCENT|StandardBlocks.CFG_HORIZIONTAL|StandardBlocks.CFG_LOOK_PLACEMENT,
    BlockBehaviour.Properties.of(Material.DECORATION, MaterialColor.COLOR_BROWN).strength(0.1f, 3f).sound(SoundType.CROP)
      .noCollission().noOcclusion().isValidSpawn((s,w,p,e)->false).jumpFactor(1.2f).randomTicks()
  )).setRegistryName(new ResourceLocation(MODID, "trail"));

  private static final Block[] modBlocks = {
    HIVE_BLOCK,
    TRAIL_BLOCK
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Items
  //--------------------------------------------------------------------------------------------------------------------

  private static Item.Properties default_item_properties()
  { return (new Item.Properties()).tab(ModAnthillInside.ITEMGROUP); }

  public static final RedSugarItem RED_SUGAR_ITEM = (RedSugarItem)((new RedSugarItem(
    default_item_properties().rarity(Rarity.UNCOMMON)
  ).setRegistryName(MODID, "red_sugar")));

  public static final AntsItem ANTS_ITEM = (AntsItem)((new AntsItem(
    TRAIL_BLOCK,
    default_item_properties().rarity(Rarity.UNCOMMON)
  ).setRegistryName(MODID, "ants")));

  private static final Item[] modItems = {
    RED_SUGAR_ITEM,
    ANTS_ITEM
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entities and entities
  //--------------------------------------------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  public static final BlockEntityType<RedAntHive.RedAntHiveTileEntity> TET_HIVE = (BlockEntityType<RedAntHive.RedAntHiveTileEntity>)(BlockEntityType.Builder
    .of(RedAntHive.RedAntHiveTileEntity::new, HIVE_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_hive"));

  private static final BlockEntityType<?>[] tile_entity_types = {
    TET_HIVE
  };

  @SuppressWarnings("all")
  private static final EntityType<?> entity_types[] = {
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Containers
  //--------------------------------------------------------------------------------------------------------------------

  public static final MenuType<RedAntHive.RedAntHiveMenu> CT_HIVE;

  static {
    CT_HIVE = (new MenuType<>(RedAntHive.RedAntHiveMenu::new));
    CT_HIVE.setRegistryName(MODID,"ct_hive");
  }

  @SuppressWarnings("all")
  private static final MenuType<?> container_types[] = {
    CT_HIVE
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  private final static List<Item> registeredItems = new ArrayList<>();

  public static List<Block> allBlocks()
  { return Arrays.asList(modBlocks); }

  public static List<Item> allItems()
  {
    if(!registeredItems.isEmpty()) return registeredItems;
    HashMap<ResourceLocation,Item> items = new HashMap<>();
    for(Item item:modItems) {
      items.put(item.getRegistryName(), item);
    }
    for(Block block:modBlocks) {
      ResourceLocation rl = block.getRegistryName();
      if(rl == null) continue;
      Item item;
      if(block instanceof StandardBlocks.IBlockItemFactory) {
        item = ((StandardBlocks.IBlockItemFactory)block).getBlockItem(block, (new Item.Properties().tab(ModAnthillInside.ITEMGROUP)));
      } else {
        item = new BlockItem(block, (new Item.Properties().tab(ModAnthillInside.ITEMGROUP)));
      }
      if((!items.containsValue(item)) && (!items.containsKey(item.getRegistryName())) ){
        items.put(rl, item.setRegistryName(rl));
      }
    }
    registeredItems.addAll(items.values());
    return registeredItems;
  }

  public static List<EntityType<?>> allEntityTypes()
  { return Arrays.asList(entity_types); }

  public static List<MenuType<?>> allContainerTypes()
  { return Arrays.asList(container_types); }

  public static List<BlockEntityType<?>> allTileEntityTypes()
  { return Arrays.asList(tile_entity_types); }

  public static boolean isExperimentalBlock(Block block)
  { return false; }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(Arrays.asList(modBlocks)); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(Arrays.asList(modItems)); }

  public static void registerContainerGuis()
  {
    MenuScreens.register(CT_HIVE, RedAntHive.RedAntHiveGui::new);
  }

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerTileEntityRenderers()
  {}

  @OnlyIn(Dist.CLIENT)
  public static void processContentClientSide()
  {
    // Block renderer selection
    for(Block block: getRegisteredBlocks()) {
      if(block instanceof IStandardBlock) {
        switch(((IStandardBlock)block).getRenderTypeHint()) {
          case CUTOUT:
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
            break;
          case CUTOUT_MIPPED:
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutoutMipped());
            break;
          case TRANSLUCENT:
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.translucent());
            break;
          case TRANSLUCENT_NO_CRUMBLING:
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.translucentNoCrumbling());
            break;
          case SOLID:
            break;
        }
      }
    }
  }

}
