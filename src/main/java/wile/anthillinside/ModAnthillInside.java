/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.detail.Auxiliaries;
import wile.anthillinside.libmc.detail.Registries;


@Mod("anthillinside")
public class ModAnthillInside
{
  public static final String MODID = "anthillinside";
  public static final String MODNAME = "Anthill Inside";
  public static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  public ModAnthillInside()
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    Registries.init(MODID, "ants", (reg)->reg.register(FMLJavaModLoadingContext.get().getModEventBus()));
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
    public static void onConfigLoad(final ModConfigEvent.Loading event)
    { ModConfig.apply(); }

    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent.Reloading event)
    { try { ModConfig.apply(); } catch(Throwable e) { Auxiliaries.logger().error("Failed to load changed config: " + e.getMessage()); } }

    public static void onBlockBroken(net.minecraftforge.event.world.BlockEvent.BreakEvent event)
    {
      if((event.getState()==null) || (event.getPlayer()==null)) return;
      RedAntHive.onGlobalPlayerBlockBrokenEvent(event.getState(), event.getWorld(), event.getPos(), event.getPlayer());
    }
  }
}
