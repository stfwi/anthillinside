/*
 * @file ModConfig.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main class for module settings. Handles reading and
 * saving the config file.
 */
package wile.anthillinside;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import wile.anthillinside.blocks.*;
import wile.anthillinside.libmc.Auxiliaries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class ModConfig
{
  private static final String MODID = ModAnthillInside.MODID;

  //--------------------------------------------------------------------------------------------------------------------
  // Optout checks
  //--------------------------------------------------------------------------------------------------------------------

  public static boolean isOptedOut(final @Nullable Block block)
  { return isOptedOut(block.asItem()); }

  public static boolean isOptedOut(final @Nullable Item item)
  { return (item!=null) && optouts_.contains(Auxiliaries.getResourceLocation(item).getPath()); }

  public static boolean withExperimental()
  { return with_experimental_features_; }

  public static boolean withoutRecipes()
  { return false; }

  public static boolean withDebugLogging()
  { return with_experimental_features_ && with_config_logging_; }

  //--------------------------------------------------------------------------------------------------------------------
  // Cache
  //--------------------------------------------------------------------------------------------------------------------

  private static final CompoundTag server_config_ = new CompoundTag();
  private static final HashSet<String> optouts_ = new HashSet<>();
  private static boolean with_experimental_features_ = false;
  private static boolean with_config_logging_ = false;

  public static CompoundTag getServerConfig()
  { return server_config_; }

  private static void updateOptouts()
  { optouts_.clear(); } // not needed yet.

  public static void apply()
  {
    //if((!COMMON_CONFIG_SPEC.isLoaded())) return;
    with_config_logging_ = false; // COMMON.with_config_logging.get();
    with_experimental_features_ = false; // COMMON.with_experimental.get();
    if(with_experimental_features_) Auxiliaries.logInfo("Config: EXPERIMENTAL FEATURES ENABLED.");
    updateOptouts();
    final List<String> unknown_items = new ArrayList<>();
    final HashMap<Item, HashSet<Item>> known_items = new HashMap<>();
    /*
    final BiConsumer<Item, List<String>> add_known_items = (ref_item, item_regnames) -> {
      known_items.putIfAbsent(ref_item, new HashSet<>());
      final HashSet<Item> items = known_items.get(ref_item);
      item_regnames.forEach((s)->{
        try {
          final ResourceLocation rl = ResourceLocation.tryParse(s);
          if(rl == null) { unknown_items.add(s); return; }
          //@todo: How to access the dang registry. Want (string)->Item
          //RegistryAccess
          //Registries.ITEM
          final Item item = null; // ForgeRegistries.ITEMS.getValue(rl);
          if(item == null) return; // Not registered, no config error (e.g. mod not installed).
          items.add(item);
        } catch(Throwable ex) {
          unknown_items.add(s);
        }
      });
    };
    */

    /// --- TEMPORARY
    {
      {
        // add_known_items.accept(Items.CRAFTING_TABLE, new ArrayList<>(List.of("minecraft:crafting_table")));
        known_items.putIfAbsent(Items.CRAFTING_TABLE, new HashSet<>());
        known_items.get(Items.CRAFTING_TABLE).add(Items.CRAFTING_TABLE);
      }
      {
        //add_known_items.accept(Items.FURNACE, new ArrayList<>(List.of("minecraft:furnace")));
        known_items.putIfAbsent(Items.FURNACE, new HashSet<>());
        known_items.get(Items.FURNACE).add(Items.FURNACE);
      }
      {
        //add_known_items.accept(Items.BLAST_FURNACE, new ArrayList<>(List.of("minecraft:blast_furnace")));
        known_items.putIfAbsent(Items.BLAST_FURNACE, new HashSet<>());
        known_items.get(Items.BLAST_FURNACE).add(Items.BLAST_FURNACE);
      }
      {
        // add_known_items.accept(Items.SMOKER, new ArrayList<>(List.of("minecraft:smoker")));
        known_items.putIfAbsent(Items.SMOKER, new HashSet<>());
        known_items.get(Items.SMOKER).add(Items.SMOKER);
      }
      {
        // add_known_items.accept(Items.COMPOSTER, new ArrayList<>(List.of("minecraft:composter")));
        known_items.putIfAbsent(Items.COMPOSTER, new HashSet<>());
        known_items.get(Items.COMPOSTER).add(Items.COMPOSTER);
      }
      {
        // add_known_items.accept(Items.BREWING_STAND, new ArrayList<>(List.of("minecraft:brewing_stand")));
        known_items.putIfAbsent(Items.BREWING_STAND, new HashSet<>());
        known_items.get(Items.BREWING_STAND).add(Items.BREWING_STAND);
      }
      {
        // add_known_items.accept(Items.GRINDSTONE, new ArrayList<>(List.of("minecraft:grindstone")));
        known_items.putIfAbsent(Items.GRINDSTONE, new HashSet<>());
        known_items.get(Items.GRINDSTONE).add(Items.GRINDSTONE);
      }
      {
        // add_known_items.accept(Items.HOPPER, new ArrayList<>(List.of("minecraft:hopper")));
        known_items.putIfAbsent(Items.HOPPER, new HashSet<>());
        known_items.get(Items.HOPPER).add(Items.HOPPER);
      }
      {
        // add_known_items.accept(Items.DROPPER, new ArrayList<>(List.of("minecraft:dropper")));
        known_items.putIfAbsent(Items.DROPPER, new HashSet<>());
        known_items.get(Items.DROPPER).add(Items.DROPPER);
      }
      {
        // add_known_items.accept(Items.DISPENSER, new ArrayList<>(List.of("minecraft:dispenser")));
        known_items.putIfAbsent(Items.DISPENSER, new HashSet<>());
        known_items.get(Items.DISPENSER).add(Items.DISPENSER);
      }
      {
        // add_known_items.accept(Items.SHEARS, new ArrayList<>(List.of("minecraft:shears")));
        known_items.putIfAbsent(Items.SHEARS, new HashSet<>());
        known_items.get(Items.SHEARS).add(Items.SHEARS);
      }
      {
        // add_known_items.accept(Items.IRON_HOE, new ArrayList<>(List.of("minecraft:iron_hoe", "minecraft:diamond_hoe", "minecraft:netherite_hoe")));
        known_items.putIfAbsent(Items.IRON_HOE, new HashSet<>());
        known_items.get(Items.IRON_HOE).add(Items.IRON_HOE);
        known_items.get(Items.IRON_HOE).add(Items.DIAMOND_HOE);
        known_items.get(Items.IRON_HOE).add(Items.NETHERITE_HOE);
      }
      {
        // add_known_items.accept(Items.IRON_AXE, new ArrayList<>(List.of("minecraft:iron_axe", "minecraft:diamond_axe", "minecraft:netherite_axe")));
        known_items.putIfAbsent(Items.IRON_AXE, new HashSet<>());
        known_items.get(Items.IRON_AXE).add(Items.IRON_AXE);
        known_items.get(Items.IRON_AXE).add(Items.DIAMOND_AXE);
        known_items.get(Items.IRON_AXE).add(Items.NETHERITE_AXE);
      }
      {
        // add_known_items.accept(Items.IRON_PICKAXE, new ArrayList<>(List.of("minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe")));
        known_items.putIfAbsent(Items.IRON_PICKAXE, new HashSet<>());
        known_items.get(Items.IRON_PICKAXE).add(Items.IRON_PICKAXE);
        known_items.get(Items.IRON_PICKAXE).add(Items.DIAMOND_PICKAXE);
        known_items.get(Items.IRON_PICKAXE).add(Items.NETHERITE_PICKAXE);
      }
      {
        // Animal food not configurable now.
        known_items.putIfAbsent(Items.WHEAT, new HashSet<>());
        known_items.get(Items.WHEAT).add(Items.WHEAT);
        known_items.get(Items.WHEAT).add(Items.WHEAT_SEEDS);
        known_items.get(Items.WHEAT).add(Items.CARROT);
      }
      {
        // Bonemeal
        known_items.putIfAbsent(Items.BONE_MEAL, new HashSet<>());
        known_items.get(Items.BONE_MEAL).add(Items.BONE_MEAL);
      }
    }
    if(!unknown_items.isEmpty()) {
      Auxiliaries.logInfo("Unknown items/invalid resource locations in config: [" + String.join(",", unknown_items) + "]");
    }
    RedAntTrail.on_config();
    // @todo: Check if there is a unified/built-in config system for fabric.
    RedAntHive.on_config(
      known_items,      // HashMap<Item, HashSet<Item>> known_items
      2,                // 0 .. 20,   int ore_mining_spawn_probability_percent
      100,              // 50 .. 150, int ant_speed_scaler_percent
      5,                // 1 .. 60,   int sugar_time_s
      120,              // 60 .. 600, int growth_latency_s
      100, 16, 3,       // 0 .. 150,  int feeding_speed_factor_percent, int feeding_entity_limit, int feeding_xz_radius
      100,              // 0 .. 150,  int farming_speed_factor_percent
      100,              // 0 .. 150   int block_breaking_speed_factor_percent
      100,              // 0 .. 150   int tree_chopping_speed_factor_percent
      100               // 0 .. 150   int tool_damage_factor_percent
    );
    /// --- /TEMPORARY default config.
  }

  public static void log(String config_message)
  {
    if(!with_config_logging_) return;
    Auxiliaries.logInfo(config_message);
  }
}
