/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Networking;
import wile.anthillinside.libmc.Overlay;
import wile.anthillinside.libmc.Registries;


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
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::addCreativeTab);
    MinecraftForge.EVENT_BUS.register(this);
    MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onBlockBroken);
  }

  public static Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.anthillinside.libmc.Networking.init(MODID);
    ModConfig.apply();
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    Overlay.TextOverlayGui.on_config(0.75);
    Networking.OverlayTextMessage.setHandler(Overlay.TextOverlayGui::show);
    ModContent.registerMenuGuis();
    ModContent.processContentClientSide();
  }

  private void addCreativeTab(CreativeModeTabEvent.BuildContents event)
  {
    if(event.getTab() != Registries.getCreativeModeTab()) return;
    Registries.getRegisteredItems().forEach(event::accept);
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

    public static void onBlockBroken(net.minecraftforge.event.level.BlockEvent.BreakEvent event)
    {
      if((event.getState()==null) || (event.getPlayer()==null)) return;
      RedAntHive.onGlobalPlayerBlockBrokenEvent(event.getState(), event.getLevel(), event.getPos(), event.getPlayer());
    }
  }

  @OnlyIn(Dist.CLIENT)
  @Mod.EventBusSubscriber(Dist.CLIENT)
  public static class ForgeClientEvents
  {
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event)
    { Overlay.TextOverlayGui.INSTANCE.onRenderGui(event.getPoseStack()); }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderWorldOverlay(net.minecraftforge.client.event.RenderLevelStageEvent event)
    {
      if(event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
        Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(event.getPoseStack(), event.getPartialTick());
      }
    }
  }

}
