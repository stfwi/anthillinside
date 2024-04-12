/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.MenuType;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.NetworkingClient;
import wile.anthillinside.libmc.Overlay;
import wile.anthillinside.libmc.Registries;


@Environment(EnvType.CLIENT)
public class ModAnthillInsideClient implements ClientModInitializer
{
  public ModAnthillInsideClient()
  {
  }

  @Override
  public void onInitializeClient()
  {
    NetworkingClient.clientInit(ModConstants.MODID);
    Overlay.register();
    registerMenuGuis();
    Overlay.TextOverlayGui.on_config(
      0.75,
      0x00ffaa00,
      0x55333333,
      0x55333333,
      0x55444444
    );
    processContentClientSide();
    WorldRenderEvents.AFTER_TRANSLUCENT.register((context)->Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(context.matrixStack(), context.tickDelta()));
    // ClientTickEvents.END_CLIENT_TICK.register(ModAnthillInsideClient::onPlayerTickEvent); private static void onPlayerTickEvent(final net.minecraft.client.Minecraft mc)  { if((mc.level==null) || (mc.level.getGameTime() & 0x1) != 0) return; }
  }

  // ----------------------------------------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static void registerMenuGuis()
  {
    MenuScreens.register((MenuType<RedAntHive.RedAntHiveMenu>)Registries.getMenuTypeOfBlock("hive"), RedAntHive.RedAntHiveGui::new);
  }

  private static void processContentClientSide()
  {
    BlockRenderLayerMap.INSTANCE.putBlock(ModContent.references.HIVE_BLOCK, RenderType.cutout());
    BlockRenderLayerMap.INSTANCE.putBlock(ModContent.references.TRAIL_BLOCK, RenderType.translucent());
  }

}
