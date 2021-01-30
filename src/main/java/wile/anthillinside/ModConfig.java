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

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.tuple.Pair;
import wile.anthillinside.blocks.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;


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
    // Optout
    public final ForgeConfigSpec.ConfigValue<String> pattern_excludes;
    public final ForgeConfigSpec.ConfigValue<String> pattern_includes;
    // MISC
    public final ForgeConfigSpec.BooleanValue with_experimental;
    public final ForgeConfigSpec.BooleanValue with_config_logging;
    /// Tweaks
    public final ForgeConfigSpec.IntValue trail_speed_percent;
    public final ForgeConfigSpec.IntValue hive_drop_chance_percent;
    public final ForgeConfigSpec.IntValue hive_processing_speed_percent;
    public final ForgeConfigSpec.IntValue hive_sugar_boost_time_s;
    public final ForgeConfigSpec.IntValue hive_growth_period_s;

    CommonConfig(ForgeConfigSpec.Builder builder)
    {
      builder.comment("Settings affecting the logical server side.")
        .push("server");
      // --- OPTOUTS ------------------------------------------------------------
      {
        builder.comment("Opt-out settings")
          .push("optout");
        pattern_excludes = builder
          .translation(MODID + ".config.pattern_excludes")
          .comment("Opt-out any block by its registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. You must match the whole name, "
            + "means maybe add '*' also at the begin and end. Example: '*wood*,*steel*' "
            + "excludes everything that has 'wood' or 'steel' in the registry name. "
            + "The matching result is also traced in the log file. ")
          .define("pattern_excludes", "");
        pattern_includes = builder
          .translation(MODID + ".config.pattern_includes")
          .comment("Prevent blocks from being opt'ed by registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. Evaluated before all other opt-out checks. "
            + "You must match the whole name, means maybe add '*' also at the begin and end. Example: "
            + "'*wood*,*steel*' includes everything that has 'wood' or 'steel' in the registry name."
            + "The matching result is also traced in the log file.")
          .define("pattern_includes", "");
        builder.pop();
      }
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
      // --- TWEAKS ------------------------------------------------------------
      {
        builder.comment("Tweak settings")
          .push("tweaks");
        trail_speed_percent = builder
          .translation(MODID + ".config.trail_speed_percent")
          .comment("Sets how fast the Red Ant Trail conveys items.")
          .defineInRange("trail_speed_percent", 100, 50, 125);
        hive_drop_chance_percent = builder
          .translation(MODID + ".config.hive_drop_chance_percent")
          .comment("Sets how probable it is that a Red Ant Hive drops when manually mining Redstone Ore.")
          .defineInRange("hive_drop_chance_percent", 3, 1, 20);
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
        builder.pop();
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Optout checks
  //--------------------------------------------------------------------------------------------------------------------

  public static final boolean isOptedOut(final @Nullable Block block)
  { return isOptedOut(block.asItem()); }

  public static final boolean isOptedOut(final @Nullable Item item)
  { return (item!=null) && optouts_.contains(item.getRegistryName().getPath()); }

  public static boolean withExperimental()
  { return with_experimental_features_; }

  public static boolean withoutRecipes()
  { return false; }

  //--------------------------------------------------------------------------------------------------------------------
  // Cache
  //--------------------------------------------------------------------------------------------------------------------

  private static final CompoundNBT server_config_ = new CompoundNBT();
  private static HashSet<String> optouts_ = new HashSet<>();
  private static boolean with_experimental_features_ = false;
  private static boolean with_config_logging_ = false;

  public static final CompoundNBT getServerConfig()
  { return server_config_; }

  private static final void updateOptouts()
  {
    final ArrayList<String> includes = new ArrayList<>();
    final ArrayList<String> excludes = new ArrayList<>();
    {
      String inc = COMMON.pattern_includes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z0-9]", "");
      if(COMMON.pattern_includes.get() != inc) COMMON.pattern_includes.set(inc);
      String[] incl = inc.split(",");
      for(int i=0; i< incl.length; ++i) {
        incl[i] = incl[i].replaceAll("[*]", ".*?");
        if(!incl[i].isEmpty()) includes.add(incl[i]);
      }
    }
    {
      String exc = COMMON.pattern_excludes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z0-9]", "");
      String[] excl = exc.split(",");
      for(int i=0; i< excl.length; ++i) {
        excl[i] = excl[i].replaceAll("[*]", ".*?");
        if(!excl[i].isEmpty()) excludes.add(excl[i]);
      }
    }
    if(!excludes.isEmpty()) log("Config pattern excludes: '" + String.join(",", excludes) + "'");
    if(!includes.isEmpty()) log("Config pattern includes: '" + String.join(",", includes) + "'");
    {
      HashSet<String> optouts = new HashSet<>();
      ModContent.getRegisteredItems().stream().filter((Item item) -> {
        if(item==null) return true;
        try {
          final String rn = item.getRegistryName().getPath();
          try {
            for(String e : includes) {
              if(rn.matches(e)) {
                return false;
              }
            }
            for(String e : excludes) {
              if(rn.matches(e)) {
                return true;
              }
            }
          } catch(Throwable ex) {
            LOGGER.error("optout include pattern failed, disabling.");
            includes.clear();
            excludes.clear();
          }
        } catch(Exception ex) {
          LOGGER.error("Exception evaluating the optout config: '"+ex.getMessage()+"'");
        }
        return false;
      }).forEach(e -> optouts.add(e.getRegistryName().getPath()));
      ModContent.getRegisteredBlocks().stream().filter(e->(e==null)||isOptedOut(e.asItem())).forEach(e->optouts.add(e.getRegistryName().getPath()));
      optouts_ = optouts;
    }
    {
      String s = String.join(",", optouts_);
      server_config_.putString("optout", s);
      if(!s.isEmpty()) log("Opt-outs:" + s);
    }
  }

  public static final void apply()
  {
    if((!COMMON_CONFIG_SPEC.isLoaded())) return;
    with_config_logging_ = COMMON.with_config_logging.get();
    with_experimental_features_ = COMMON.with_experimental.get();
    if(with_experimental_features_) LOGGER.info("Config: EXPERIMENTAL FEATURES ENABLED.");
    updateOptouts();
    RedAntTrail.on_config(COMMON.trail_speed_percent.get());
    RedAntHive.on_config(
      COMMON.hive_drop_chance_percent.get(),
      COMMON.hive_processing_speed_percent.get(),
      COMMON.hive_sugar_boost_time_s.get(),
      COMMON.hive_growth_period_s.get()
    );
  }

  public static final void log(String config_message)
  {
    if(!with_config_logging_) return;
    LOGGER.info(config_message);
  }
}