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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.apache.commons.lang3.tuple.Pair;
import wile.anthillinside.blocks.*;
import wile.anthillinside.libmc.Auxiliaries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;


public class ModConfig
{
  private static final Logger LOGGER = ModAnthillInside.LOGGER;
  private static final String MODID = ModAnthillInside.MODID;
  public static final CommonConfig COMMON;
  public static final ForgeConfigSpec COMMON_CONFIG_SPEC;

  static {
    final Pair<CommonConfig, ForgeConfigSpec> common_ = (new ForgeConfigSpec.Builder()).configure(CommonConfig::new);
    COMMON_CONFIG_SPEC = common_.getRight();
    COMMON = common_.getLeft();
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static class CommonConfig
  {
    // MISC
    public final ForgeConfigSpec.BooleanValue with_experimental;
    public final ForgeConfigSpec.BooleanValue with_config_logging;
    /// Tweaks
    public final ForgeConfigSpec.IntValue hive_drop_chance_percent;
    public final ForgeConfigSpec.IntValue hive_processing_speed_percent;
    public final ForgeConfigSpec.IntValue hive_sugar_boost_time_s;
    public final ForgeConfigSpec.IntValue hive_growth_period_s;
    public final ForgeConfigSpec.IntValue hive_animal_feeding_speed_percent;
    public final ForgeConfigSpec.IntValue hive_farming_speed_percent;
    /// Tools/Hive control items. Intentionally not done with TAGs because small mods should not spam the game with tags.
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> crafting_tables;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> furnaces;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> blast_furnaces;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> smokers;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> hoppers;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> composters;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> brewing_stands;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> grindstones;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> dispensers;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> droppers;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> shears;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> hoes;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> axes;
    public final ForgeConfigSpec.ConfigValue<ArrayList<String>> pickaxes;

    CommonConfig(ForgeConfigSpec.Builder builder)
    {
      builder.comment("Settings affecting the logical server side.")
        .push("server");
      // --- MISC ---------------------------------------------------------------
      {
        builder.comment("Miscellaneous settings")
          .push("miscellaneous");
        with_experimental = builder
          .translation(MODID + ".config.with_experimental")
          .comment("Enables experimental features. Use at own risk.")
          .define("with_experimental", false);
        with_config_logging = builder
          .translation(MODID + ".config.with_config_logging")
          .comment("Enable detailed logging of the config values and resulting calculations in each mod feature config.")
          .define("with_config_logging", false);
        builder.pop();
      }
      // --- TWEAKS -------------------------------------------------------------
      {
        builder.comment("Tweak settings")
          .push("tweaks");
        hive_drop_chance_percent = builder
          .translation(MODID + ".config.hive_drop_chance_percent")
          .comment("Sets how probable it is that a Red Ant Hive drops when manually mining Redstone Ore. "+
                   "The value 0 disables drops from Redstone mining (you need to add your own recipe in a data pack).")
          .defineInRange("hive_drop_chance_percent", 3, 0, 20);
        hive_processing_speed_percent = builder
          .translation(MODID + ".config.hive_processing_speed_percent")
          .comment("Sets how fast the ant processing part of work process is (in percent).")
          .defineInRange("hive_processing_speed_percent", 100, 50, 150);
        hive_sugar_boost_time_s = builder
          .translation(MODID + ".config.hive_sugar_boost_time_s")
          .comment("Sets how long the Red Sugar boost lasts (in seconds).")
          .defineInRange("hive_sugar_boost_time_s", 5, 1, 60);
        hive_growth_period_s = builder
          .translation(MODID + ".config.hive_growth_period_s")
          .comment("Sets how long it takes (seconds, in average) to generate one new ant in a hive.")
          .defineInRange("hive_growth_period_s", 120, 60, 600);
        hive_animal_feeding_speed_percent = builder
          .translation(MODID + ".config.hive_animal_feeding_speed_percent")
          .comment("Tunes the internal delay between animals are fed. The value 0 disables animal feeding.")
          .defineInRange("hive_animal_feeding_speed_percent", 100, 0, 150);
        hive_farming_speed_percent = builder
          .translation(MODID + ".config.hive_farming_speed_percent")
          .comment("Tunes the internal delay between harvesting crops. The value 0 disables harvesting.")
          .defineInRange("hive_farming_speed_percent", 100, 0, 150);
        builder.pop();
      }
      // --- KNOWN TOOLS --------------------------------------------------------
      {
        builder.pop().comment("Settings where you can explicitly define which tools or other control items the Hive knows.")
                .push("hive_control_items");
        crafting_tables = builder
                .translation(MODID + ".config.crafting_tables")
                .comment("Items that can be placed into the Hive control slot to activate ~normal crafting~.")
                .define("crafting_tables", new ArrayList<>(List.of("minecraft:crafting_table")));
        furnaces = builder
                .translation(MODID + ".config.furnaces")
                .comment("Items that can be placed into the Hive control slot to activate ~smelting or cooking~.")
                .define("furnaces", new ArrayList<>(List.of("minecraft:furnace")));
        blast_furnaces = builder
                .translation(MODID + ".config.blast_furnaces")
                .comment("Items that can be placed into the Hive control slot to activate ~fast smelting~.")
                .define("blast_furnaces", new ArrayList<>(List.of("minecraft:blast_furnace")));
        smokers = builder
                .translation(MODID + ".config.smokers")
                .comment("Items that can be placed into the Hive control slot to activate ~fast cooking~.")
                .define("smokers", new ArrayList<>(List.of("minecraft:smoker")));
        composters = builder
                .translation(MODID + ".config.composters")
                .comment("Items that can be placed into the Hive control slot to activate ~composting~.")
                .define("composters", new ArrayList<>(List.of("minecraft:composter")));
        brewing_stands = builder
                .translation(MODID + ".config.brewing_stands")
                .comment("Items that can be placed into the Hive control slot to activate ~potion brewing~.")
                .define("brewing_stands", new ArrayList<>(List.of("minecraft:brewing_stand")));
        grindstones = builder
                .translation(MODID + ".config.grindstones")
                .comment("Items that can be placed into the Hive control slot to activate ~disenchanting~.")
                .define("grindstones", new ArrayList<>(List.of("minecraft:grindstone")));
        hoppers = builder
                .translation(MODID + ".config.hoppers")
                .comment("Items that can be placed into Hive slots to activate ~item pass through (control slot), item input drawing, and item output inserting~.")
                .define("hoppers", new ArrayList<>(List.of("minecraft:hopper")));
        droppers = builder
                .translation(MODID + ".config.droppers")
                .comment("Items that can be placed into the Hive output slot to activate ~item drop output (soft eject)~.")
                .define("droppers", new ArrayList<>(List.of("minecraft:dropper")));
        dispensers = builder
                .translation(MODID + ".config.dispensers")
                .comment("Items that can be placed into the Hive output slot to activate ~item dispense output (fast eject)~.")
                .define("dispensers", new ArrayList<>(List.of("minecraft:dispenser")));
        shears = builder
                .translation(MODID + ".config.shears")
                .comment("Items that can be placed into the Hive control slot to activate ~animal or plant shearing~.")
                .define("shears", new ArrayList<>(List.of("minecraft:shears")));
        hoes = builder
                .translation(MODID + ".config.hoes")
                .comment("Items that can be placed into the Hive control slot to activate ~farmning~.")
                .define("hoes", new ArrayList<>(List.of("minecraft:iron_hoe", "minecraft:diamond_hoe", "minecraft:netherite_hoe")));
        axes = builder
                .translation(MODID + ".config.axes")
                .comment("Items that can be placed into the Hive control slot to activate ~tree chopping~.")
                .define("axes", new ArrayList<>(List.of("minecraft:iron_axe", "minecraft:diamond_axe", "minecraft:netherite_axe")));
        pickaxes = builder
                .translation(MODID + ".config.pickaxes")
                .comment("Items that can be placed into the Hive control slot to activate ~block breaking~.")
                .define("pickaxes", new ArrayList<>(List.of("minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe")));
        builder.pop();
      }
    }
  }

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
    if((!COMMON_CONFIG_SPEC.isLoaded())) return;
    with_config_logging_ = COMMON.with_config_logging.get();
    with_experimental_features_ = COMMON.with_experimental.get();
    if(with_experimental_features_) LOGGER.info("Config: EXPERIMENTAL FEATURES ENABLED.");
    updateOptouts();
    final List<String> unknown_items = new ArrayList<>();
    final HashMap<Item, HashSet<Item>> known_items = new HashMap<>();
    final BiConsumer<Item, List<String>> add_known_items = (ref_item, item_regnames) -> {
      known_items.putIfAbsent(ref_item, new HashSet<>());
      final HashSet<Item> items = known_items.get(ref_item);
      item_regnames.forEach((s)->{
        try {
          final ResourceLocation rl = ResourceLocation.tryParse(s);
          if(rl == null) { unknown_items.add(s); return; }
          final Item item = ForgeRegistries.ITEMS.getValue(rl);
          if(item == null) return; // Not registered, no config error (e.g. mod not installed).
          items.add(item);
        } catch(Throwable ex) {
          unknown_items.add(s);
        }
      });
    };
    add_known_items.accept(Items.CRAFTING_TABLE, COMMON.crafting_tables.get());
    add_known_items.accept(Items.FURNACE, COMMON.furnaces.get());
    add_known_items.accept(Items.BLAST_FURNACE, COMMON.blast_furnaces.get());
    add_known_items.accept(Items.SMOKER, COMMON.smokers.get());
    add_known_items.accept(Items.COMPOSTER, COMMON.composters.get());
    add_known_items.accept(Items.BREWING_STAND, COMMON.brewing_stands.get());
    add_known_items.accept(Items.GRINDSTONE, COMMON.grindstones.get());
    add_known_items.accept(Items.HOPPER, COMMON.hoppers.get());
    add_known_items.accept(Items.DROPPER, COMMON.droppers.get());
    add_known_items.accept(Items.DISPENSER, COMMON.dispensers.get());
    add_known_items.accept(Items.SHEARS, COMMON.shears.get());
    add_known_items.accept(Items.IRON_HOE, COMMON.hoes.get());
    add_known_items.accept(Items.IRON_AXE, COMMON.axes.get());
    add_known_items.accept(Items.IRON_PICKAXE, COMMON.pickaxes.get());
    {
      known_items.putIfAbsent(Items.BONE_MEAL, new HashSet<>());
      known_items.get(Items.BONE_MEAL).add(Items.BONE_MEAL);
    }
    {
      // Animal food not configurable now.
      known_items.putIfAbsent(Items.WHEAT, new HashSet<>());
      known_items.get(Items.WHEAT).add(Items.WHEAT);
      known_items.get(Items.WHEAT).add(Items.WHEAT_SEEDS);
      known_items.get(Items.WHEAT).add(Items.CARROT);
    }
    if(!unknown_items.isEmpty()) {
      Auxiliaries.logInfo("Unknown items/invalid resource locations in config: [" + String.join(",", unknown_items) + "]");
    }
    RedAntTrail.on_config();
    RedAntHive.on_config(
      known_items,
      COMMON.hive_drop_chance_percent.get(),
      COMMON.hive_processing_speed_percent.get(),
      COMMON.hive_sugar_boost_time_s.get(),
      COMMON.hive_growth_period_s.get(),
      COMMON.hive_animal_feeding_speed_percent.get(),16, 3,
      COMMON.hive_farming_speed_percent.get(),
      100,
      100,
      100
    );
  }

  public static void log(String config_message)
  {
    if(!with_config_logging_) return;
    LOGGER.info(config_message);
  }
}
