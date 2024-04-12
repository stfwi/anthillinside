/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import wile.anthillinside.blocks.QueensLair;
import wile.anthillinside.blocks.RedAntCoveredTrail;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.blocks.RedAntTrail;
import wile.anthillinside.items.AntsItem;
import wile.anthillinside.items.RedSugarItem;
import wile.anthillinside.libmc.StandardBlocks;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Registries;

public class ModContent
{
  private static final String MODID = ModConstants.MODID;

  public static void init()
  {
    initTags();
    initBlocks();
    initItems();
  }

  public static void initTags()
  {
    // @todo: IMPLEMENT
    // Registries.addOptionaItemTag("fertilizers", new ResourceLocation("minecraft","bonemeal"));
  }

  public static void initBlocks()
  {
    Registries.addBlock("hive",
      ()->new RedAntHive.RedAntHiveBlock(
        StandardBlocks.CFG_CUTOUT|StandardBlocks.CFG_WATERLOGGABLE|StandardBlocks.CFG_LOOK_PLACEMENT,
        BlockBehaviour.Properties.of().strength(0.3f, 6f).sound(SoundType.STONE).forceSolidOn().isValidSpawn((s,w,p,e)->false),
        new AABB[]{ Auxiliaries.getPixeledAABB(0,0,0,16,16, 16) }
      ),
      RedAntHive.RedAntHiveTileEntity::new,
      RedAntHive.RedAntHiveMenu::new
    );
    Registries.addBlock("trail",
      ()->new RedAntTrail.RedAntTrailBlock(
        StandardBlocks.CFG_TRANSLUCENT|StandardBlocks.CFG_HORIZIONTAL|StandardBlocks.CFG_LOOK_PLACEMENT,
        BlockBehaviour.Properties.of().strength(0.1f, 3f).sound(SoundType.CROP).noCollission().noOcclusion().isValidSpawn((s,w,p,e)->false).jumpFactor(1.2f).randomTicks()
      ),
      Registries.WITHOUT_ITEM
    );
    Registries.addBlock("covered_trail",
      ()->new RedAntCoveredTrail.RedAntCoveredTrailBlock(
        StandardBlocks.CFG_CUTOUT|StandardBlocks.CFG_FACING_PLACEMENT,
        BlockBehaviour.Properties.of().strength(0.1f, 3f).sound(SoundType.WOOD).noCollission().isValidSpawn((s,w,p,e)->false),
        new AABB[]{ Auxiliaries.getPixeledAABB(0,0,0,16,16, 16) }
      ),
      RedAntCoveredTrail.RedAntCoveredTrailTileEntity::new
    );
    Registries.addBlock("queens_lair",
      ()->new QueensLair.QueensLairBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().strength(0.1f, 3f).sound(SoundType.CROP).noCollission().noOcclusion().isValidSpawn((s,w,p,e)->false).randomTicks(),
        new AABB[]{Auxiliaries.getPixeledAABB(3.0, 0.0, 3.0, 13.0, 10.0, 13.0)}
      )
    );
  }

  public static void initItems()
  {
    Registries.addItem("red_sugar", ()->new RedSugarItem((new Item.Properties()).rarity(Rarity.UNCOMMON)));
    Registries.addItem("ants", ()->new AntsItem((new Item.Properties()).rarity(Rarity.UNCOMMON)));
  }

  public static void initReferences()
  {
    Registries.instantiateAll();
    references.ANTS_ITEM = (AntsItem)Registries.getItem("ants");
    references.RED_SUGAR_ITEM = (RedSugarItem)Registries.getItem("red_sugar");
    references.HIVE_BLOCK = (RedAntHive.RedAntHiveBlock)Registries.getBlock("hive");
    references.TRAIL_BLOCK = (RedAntTrail.RedAntTrailBlock)Registries.getBlock("trail");
    references.COVERED_TRAIL_BLOCK = (RedAntCoveredTrail.RedAntCoveredTrailBlock)Registries.getBlock("covered_trail");
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Holders for performance critical reference accesses.
  //--------------------------------------------------------------------------------------------------------------------

  public static final class references
  {
    public static AntsItem ANTS_ITEM = null;
    public static RedSugarItem RED_SUGAR_ITEM = null;
    public static RedAntHive.RedAntHiveBlock HIVE_BLOCK = null;
    public static RedAntTrail.RedAntTrailBlock TRAIL_BLOCK = null;
    public static RedAntCoveredTrail.RedAntCoveredTrailBlock COVERED_TRAIL_BLOCK = null;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  @Environment(EnvType.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerMenuGuis()
  {
    MenuScreens.register((MenuType<RedAntHive.RedAntHiveMenu>)Registries.getMenuTypeOfBlock("hive"), RedAntHive.RedAntHiveGui::new);
  }

  @Environment(EnvType.CLIENT)
  public static void processContentClientSide()
  {}
}
