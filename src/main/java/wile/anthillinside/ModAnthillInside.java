/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.detail.*;


@Mod("anthillinside")
public class ModAnthillInside
{
  public static final String MODID = "anthillinside";
  public static final String MODNAME = "Anthill Inside";
  public static final Logger LOGGER = LogManager.getLogger();

  public ModAnthillInside()
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    //FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeEvents::onConfigLoad);
    //FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeEvents::onConfigReload);
    MinecraftForge.EVENT_BUS.register(this);
    MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onBlockBroken);
  }

  public static Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.anthillinside.libmc.detail.Networking.init(MODID);
    ModConfig.apply();
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    wile.anthillinside.libmc.detail.Overlay.register();
    ModContent.registerTileEntityRenderers();
    ModContent.registerContainerGuis();
    ModContent.processContentClientSide();
  }

  @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class ForgeEvents
  {
    @SubscribeEvent
    public static void onBlocksRegistry(final RegistryEvent.Register<Block> event)
    { ModContent.allBlocks().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onItemRegistry(final RegistryEvent.Register<Item> event)
    {
      ModContent.allItems().forEach(e->event.getRegistry().register(e));
      ItemTags.createOptional(new ResourceLocation(MODID, "fertilizers"));
    }

    @SubscribeEvent
    public static void onTileEntityRegistry(final RegistryEvent.Register<BlockEntityType<?>> event)
    { ModContent.allTileEntityTypes().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onRegisterEntityTypes(final RegistryEvent.Register<EntityType<?>> event)
    { ModContent.allEntityTypes().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onRegisterContainerTypes(final RegistryEvent.Register<MenuType<?>> event)
    { ModContent.allContainerTypes().forEach(e->event.getRegistry().register(e)); }

//    @SubscribeEvent
//    public static void onConfigLoad(IConfigEvent event)
//    {
//      ModConfig.apply(); // config ModConfig.Loading/ModConfig.Reloading
//    }

    public static void onBlockBroken(net.minecraftforge.event.world.BlockEvent.BreakEvent event)
    {
      if((event.getState()==null) || (event.getPlayer()==null)) return;
      RedAntHive.onGlobalPlayerBlockBrokenEvent(event.getState(), event.getWorld(), event.getPos(), event.getPlayer());
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Item group / creative tab
  // -------------------------------------------------------------------------------------------------------------------

  public static final CreativeModeTab ITEMGROUP = (new CreativeModeTab("tab" + MODID) {
    @OnlyIn(Dist.CLIENT)
    public ItemStack makeIcon()
    { return new ItemStack(ModContent.ANTS_ITEM); }
  });
}
