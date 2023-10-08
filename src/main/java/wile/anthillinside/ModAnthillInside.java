/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.anthillinside;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import wile.anthillinside.blocks.RedAntHive;
import wile.anthillinside.items.AntsItem;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Networking;
import wile.anthillinside.libmc.Registries;


public class ModAnthillInside implements ModInitializer
{
  public static final String MODID = "anthillinside";
  public static final String MODNAME = "Anthill Inside";
  public static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  public ModAnthillInside()
  {
    Auxiliaries.init(MODID, CompoundTag::new);
    Auxiliaries.logGitVersion(MODNAME);
  }

  public void onInitialize()
  {
    Registries.init(MODID, "hive");
    Networking.init(MODID);
    ModContent.init(MODID);
    ModContent.initReferences();
    ModConfig.apply();
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(reg-> Registries.getRegisteredItems().forEach(reg::accept) );
    PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity)->RedAntHive.onGlobalPlayerBlockBrokenEvent(state, world, pos, player));
  }
}
