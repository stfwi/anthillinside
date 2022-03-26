/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.detail.Auxiliaries;
import wile.anthillinside.libmc.detail.Registries;


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
    Registries.init(MODID, "ants");
    ModContent.init(MODID);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
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
    ModContent.registerMenuGuis();
    ModContent.processContentClientSide();
  }

  @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class ForgeEvents
  {
    @SubscribeEvent
    public static void onBlocksRegistry(final RegistryEvent.Register<Block> event)
    { Registries.onBlockRegistry((rl, block)->event.getRegistry().register(block)); }

    @SubscribeEvent
    public static void onItemRegistry(final RegistryEvent.Register<Item> event)
    { Registries.onItemRegistry((rl, item)->event.getRegistry().register(item)); }

    @SubscribeEvent
    public static void onTileEntityRegistry(final RegistryEvent.Register<BlockEntityType<?>> event)
    { Registries.onBlockEntityRegistry((rl, tet)->event.getRegistry().register(tet)); }

    @SubscribeEvent
    public static void onRegisterContainerTypes(final RegistryEvent.Register<MenuType<?>> event)
    { Registries.onMenuTypeRegistry((rl, ct)->event.getRegistry().register(ct)); }

    //@SubscribeEvent
    //public static void onConfigLoad(IConfigEvent event)
    //{ ModConfig.apply(); // config ModConfig.Loading/ModConfig.Reloading }

    public static void onBlockBroken(net.minecraftforge.event.world.BlockEvent.BreakEvent event)
    {
      if((event.getState()==null) || (event.getPlayer()==null)) return;
      RedAntHive.onGlobalPlayerBlockBrokenEvent(event.getState(), event.getWorld(), event.getPos(), event.getPlayer());
    }
  }
}
