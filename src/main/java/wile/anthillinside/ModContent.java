/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ObjectHolder;
import org.slf4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.blocks.RedAntTrail;
import wile.anthillinside.items.AntsItem;
import wile.anthillinside.items.RedSugarItem;
import wile.anthillinside.libmc.StandardBlocks;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Registries;

public class ModContent
{
  private static final Logger LOGGER = ModAnthillInside.LOGGER;
  private static final String MODID = ModAnthillInside.MODID;

  private static class detail {
    public static String MODID = "";
  }

  public static void init(String modid)
  {
    detail.MODID = modid;
    initTags();
    initBlocks();
    initItems();
  }

  public static void initTags()
  {
    Registries.addOptionaItemTag("fertilizers", new ResourceLocation("minecraft","bonemeal"));
  }

  public static void initBlocks()
  {
    Registries.addBlock("hive",
      ()->new RedAntHive.RedAntHiveBlock(
        StandardBlocks.CFG_CUTOUT|StandardBlocks.CFG_WATERLOGGABLE|StandardBlocks.CFG_LOOK_PLACEMENT,
        BlockBehaviour.Properties.of(Material.STONE, MaterialColor.STONE).strength(0.3f, 6f).sound(SoundType.STONE),
        new AABB[]{
          Auxiliaries.getPixeledAABB(1,1,0,15,15, 1),
          Auxiliaries.getPixeledAABB(0,0,1,16,16,16),
        }
      ),
      RedAntHive.RedAntHiveTileEntity::new,
      RedAntHive.RedAntHiveMenu::new
    );
    Registries.addBlock("trail",
      ()->new RedAntTrail.RedAntTrailBlock(
        StandardBlocks.CFG_TRANSLUCENT|StandardBlocks.CFG_HORIZIONTAL|StandardBlocks.CFG_LOOK_PLACEMENT,
        BlockBehaviour.Properties.of(Material.STONE, MaterialColor.COLOR_BROWN).strength(0.1f, 3f).sound(SoundType.CROP).noCollission().noOcclusion().isValidSpawn((s,w,p,e)->false).jumpFactor(1.2f).randomTicks()
      ),
      Registries.WITHOUT_ITEM
    );
  }

  public static void initItems()
  {
    Registries.addItem("red_sugar", ()->new RedSugarItem((new Item.Properties()).rarity(Rarity.UNCOMMON)));
    Registries.addItem("ants", ()->new AntsItem((new Item.Properties()).rarity(Rarity.UNCOMMON)));
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Holders for performance critical reference accesses.
  //--------------------------------------------------------------------------------------------------------------------

  @ObjectHolder(registryName="item", value="anthillinside:ants")
  public static final AntsItem ANTS_ITEM = null;

  @ObjectHolder(registryName="item", value="anthillinside:red_sugar")
  public static final RedSugarItem RED_SUGAR_ITEM = null;

  @ObjectHolder(registryName="block", value="anthillinside:hive")
  public static final RedAntHive.RedAntHiveBlock HIVE_BLOCK = null;

  @ObjectHolder(registryName="block", value="anthillinside:trail")
  public static final RedAntTrail.RedAntTrailBlock TRAIL_BLOCK = null;

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerMenuGuis()
  {
    MenuScreens.register((MenuType<RedAntHive.RedAntHiveMenu>)Registries.getMenuTypeOfBlock("hive"), RedAntHive.RedAntHiveGui::new);
  }

  @OnlyIn(Dist.CLIENT)
  public static void processContentClientSide()
  {
    /*
    -> Now in the model  JSON files. But wait wait. It will be there again in a while. Who knows.
    for(Block block: Registries.getRegisteredBlocks()) {
      if(block instanceof StandardBlocks.IStandardBlock) {
        switch(((StandardBlocks.IStandardBlock)block).getRenderTypeHint()) {
          case CUTOUT: net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, net.minecraft.client.renderer.RenderType.cutout()); break;
          case CUTOUT_MIPPED: net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, net.minecraft.client.renderer.RenderType.cutoutMipped()); break;
          case TRANSLUCENT: net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, net.minecraft.client.renderer.RenderType.translucent()); break;
          case TRANSLUCENT_NO_CRUMBLING: net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, net.minecraft.client.renderer.RenderType.translucentNoCrumbling()); break;
          case SOLID: break;
        }
      }
    }
    */
  }
}
