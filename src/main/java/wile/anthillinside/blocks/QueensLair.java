//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package wile.anthillinside.blocks;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import wile.anthillinside.ModContent;
import wile.anthillinside.libmc.Auxiliaries;
import wile.anthillinside.libmc.Overlay;
import wile.anthillinside.libmc.StandardBlocks;


@SuppressWarnings("deprecation")
public class QueensLair
{
  private static int use_health_restore_probability_percent = 50;
  private static int rndtick_health_loss_probability_percent = 30;
  private static int rndtick_growth_probability_percent = 30;
  private static boolean trace_progress_and_health = false;

  public static void on_config(int health_restore_probability_percent, int health_loss_probability_percent, int growth_probability_percent, boolean enable_log_tracing) {
    use_health_restore_probability_percent = Mth.clamp(health_restore_probability_percent, 10, 100);
    rndtick_health_loss_probability_percent = Mth.clamp(health_loss_probability_percent, 10, 90);
    rndtick_growth_probability_percent = Mth.clamp(growth_probability_percent, 10, 90);
    trace_progress_and_health = enable_log_tracing;
  }

  public static class QueensLairBlock extends StandardBlocks.Cutout
  {
    public static final IntegerProperty HEALTH = IntegerProperty.create("health", 0, 7);
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 3);
    private static final int MAX_HEALTH = HEALTH.getPossibleValues().stream().max(Integer::compareTo).orElseThrow();
    private static final int MAX_GROWTH = GROWTH.getPossibleValues().stream().max(Integer::compareTo).orElseThrow();

    public QueensLairBlock(long config, BlockBehaviour.Properties properties, AABB[] aabbs)
    {
      super(config, properties, aabbs);
      registerDefaultState(super.defaultBlockState().setValue(HEALTH, 1).setValue(GROWTH, 0));
    }

    public boolean hasDynamicDropList()
    { return true; }

    public List<ItemStack> dropList(BlockState state, Level world, BlockEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(asItem())); }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(HEALTH, GROWTH); }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    { return super.getStateForPlacement(context); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr)
    {
      if(world.isClientSide()) return ItemInteractionResult.SUCCESS;
      final ItemStack red_sugar = player.getItemInHand(hand);
      if(red_sugar.is(ModContent.references.RED_SUGAR_ITEM) && state.getValue(HEALTH) < MAX_HEALTH) {
        if(!player.isCreative()) red_sugar.shrink(1);
        if(red_sugar.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
        if(world.getRandom().nextInt(0, 100) < QueensLair.use_health_restore_probability_percent) {
          state = state.setValue(HEALTH, state.getValue(HEALTH) + 1);
          world.setBlock(pos, state, 10);
          Auxiliaries.particles(world, pos, ParticleTypes.INSTANT_EFFECT);
        } else {
          Auxiliaries.particles(world, pos, ParticleTypes.SMOKE);
        }
      }
      Overlay.show((ServerPlayer)player, Auxiliaries.localizable("block." + Auxiliaries.modid() + ".queens_lair.health" + state.getValue(HEALTH)));
      return ItemInteractionResult.CONSUME;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rand)
    {
      if(world.isClientSide()) return;
      final int health = state.getValue(HEALTH);
      final int growth = state.getValue(GROWTH);
      if(rand.nextInt(0, 100) < QueensLair.rndtick_health_loss_probability_percent) {
        if(health > 0) {
          state = state.setValue(HEALTH, health - 1);
          world.setBlock(pos, state, 10);
          Auxiliaries.particles(world, pos, ParticleTypes.ANGRY_VILLAGER);
        } else if(growth > 0 && rand.nextInt(0, 100) < QueensLair.rndtick_growth_probability_percent) {
          state = state.setValue(GROWTH, growth - 1);
          world.setBlock(pos, state, 10);
          Auxiliaries.particles(world, pos, ParticleTypes.COMPOSTER);
        }
      } else {
        final int growth_chance = QueensLair.rndtick_growth_probability_percent * health / MAX_HEALTH;
        if(rand.nextInt(0, 100) < growth_chance) {
          if(growth >= MAX_GROWTH) {
            world.setBlock(pos, ModContent.references.HIVE_BLOCK.defaultBlockState(), 11);
            Auxiliaries.playSound(world, null, pos, SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.BLOCKS, 1.0, 1.1);
            Auxiliaries.particles(world, Vec3.atCenterOf(pos), ParticleTypes.INSTANT_EFFECT, 10);
          } else {
            state = state.setValue(GROWTH, growth + 1);
            world.setBlock(pos, state, 10);
            Auxiliaries.particles(world, Vec3.atCenterOf(pos).add(0.0, 0.2, 0.0), ParticleTypes.INSTANT_EFFECT, 5);
          }
        }
      }
      if (QueensLair.trace_progress_and_health) {
        Auxiliaries.logInfo("Queens-Lair: health:" + health + "->" + state.getValue(HEALTH) + ", growth:" + growth + "->" + state.getValue(GROWTH));
      }
    }
  }
}
