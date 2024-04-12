/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Networking;
import wile.anthillinside.libmc.Registries;


public class ModAnthillInside implements ModInitializer
{
  public ModAnthillInside()
  {
    Auxiliaries.init();
    Auxiliaries.logGitVersion();
  }

  public void onInitialize()
  {
    Registries.init();
    Networking.init();
    ModContent.init();
    ModContent.initReferences();
    ModConfig.apply();
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(reg-> Registries.getRegisteredItems().forEach(reg::accept) );
    PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity)->RedAntHive.onGlobalPlayerBlockBrokenEvent(state, world, pos, player));
    Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(ModConstants.MODID, "creative_tab"), CREATIVE_TAB);
  }

  private static final CreativeModeTab CREATIVE_TAB = FabricItemGroup.builder()
    .title(Component.translatable("itemGroup.tab" + ModConstants.MODID))
    .icon(()->new ItemStack(Registries.getItem("ants")))
    .displayItems((ctx,reg)-> Registries.getRegisteredItems().forEach(reg::accept))
    .build();
}
