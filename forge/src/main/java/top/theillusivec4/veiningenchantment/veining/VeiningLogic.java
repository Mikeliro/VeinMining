package top.theillusivec4.veiningenchantment.veining;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CommandBlockBlock;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.StructureBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import top.theillusivec4.veiningenchantment.VeiningEnchantmentMod;
import top.theillusivec4.veiningenchantment.config.VeiningEnchantmentConfig;

public class VeiningLogic {

  private static final Direction[] CARDINAL_DIRECTIONS =
      new Direction[] {Direction.DOWN, Direction.UP, Direction.EAST, Direction.WEST,
          Direction.NORTH, Direction.SOUTH};

  public static void veining(ServerPlayerEntity playerEntity, BlockPos pos, Block source) {
    ServerWorld world = playerEntity.getServerWorld();
    int veiningLevels = EnchantmentHelper
        .getEnchantmentLevel(VeiningEnchantmentMod.VEINING, playerEntity.getHeldItemMainhand());

    if (veiningLevels <= 0 || playerEntity.isCrouching()) {
      return;
    }
    int blocks = 0;
    int maxBlocks = VeiningEnchantmentConfig.Veining.maxBlocksPerLevel * veiningLevels;
    int maxDistance = VeiningEnchantmentConfig.Veining.maxDistancePerLevel * veiningLevels;
    Set<BlockPos> visited = new HashSet<>();
    visited.add(pos);
    LinkedList<Tuple<BlockPos, Integer>> candidates = new LinkedList<>();
    search(candidates, pos, 1);

    while (!candidates.isEmpty()) {
      Tuple<BlockPos, Integer> candidate = candidates.poll();
      BlockPos blockPos = candidate.getA();
      int blockDistance = candidate.getB();

      if (blocks < maxBlocks && blockDistance < maxDistance && visited.add(blockPos) &&
          matches(source, world.getBlockState(blockPos).getBlock()) &&
          harvest(playerEntity, blockPos)) {
        search(candidates, blockPos, blockDistance + 1);
        blocks++;
      }
    }
  }

  private static void search(LinkedList<Tuple<BlockPos, Integer>> candidates, BlockPos source,
                             int distance) {

    for (Direction direction : CARDINAL_DIRECTIONS) {
      candidates.add(new Tuple<>(source.offset(direction), distance));
    }

    if (VeiningEnchantmentConfig.Veining.diagonalMining) {
      BlockPos up = source.offset(Direction.UP);
      BlockPos down = source.offset(Direction.DOWN);
      Direction[] yOffsets = new Direction[] {Direction.SOUTH, Direction.SOUTH, Direction.NORTH, Direction.NORTH};
      Direction[] xOffsets = new Direction[] {Direction.EAST, Direction.WEST, Direction.EAST, Direction.WEST};

      for (int i = 0; i < yOffsets.length; i++) {
        candidates.add(new Tuple<>(up.offset(yOffsets[i]).offset(xOffsets[i]), distance));
        candidates.add(new Tuple<>(down.offset(yOffsets[i]).offset(xOffsets[i]), distance));
      }
    }
  }

  public static boolean harvest(ServerPlayerEntity player, BlockPos pos) {
    ServerWorld world = player.getServerWorld();
    BlockState blockstate = world.getBlockState(pos);
    GameType gameType = player.interactionManager.getGameType();
    int exp = net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(world, gameType, player, pos);

    if (exp == -1) {
      return false;
    } else {
      TileEntity tileentity = world.getTileEntity(pos);
      Block block = blockstate.getBlock();

      if ((block instanceof CommandBlockBlock || block instanceof StructureBlock ||
          block instanceof JigsawBlock) && !player.canUseCommandBlock()) {
        world.notifyBlockUpdate(pos, blockstate, blockstate, 3);
        return false;
      } else if (player.getHeldItemMainhand().onBlockStartBreak(pos, player)) {
        return false;
      } else if (player.blockActionRestricted(world, pos, gameType)) {
        return false;
      } else {

        if (gameType.isCreative()) {
          removeBlock(player, pos, false);
        } else {
          ItemStack itemstack = player.getHeldItemMainhand();
          ItemStack itemstack1 = itemstack.copy();
          boolean flag1 = blockstate.canHarvestBlock(world, pos, player);
          itemstack.onBlockDestroyed(world, blockstate, pos, player);
          if (itemstack.isEmpty() && !itemstack1.isEmpty()) {
            net.minecraftforge.event.ForgeEventFactory
                .onPlayerDestroyItem(player, itemstack1, Hand.MAIN_HAND);
          }
          boolean flag = removeBlock(player, pos, flag1);

          if (flag && flag1) {
            block.harvestBlock(world, player, pos, blockstate, tileentity, itemstack1);
          }

          if (flag && exp > 0) {
            blockstate.getBlock().dropXpOnBlockBreak(world, pos, exp);
          }
        }
        return true;
      }
    }
  }

  private static boolean removeBlock(PlayerEntity player, BlockPos pos, boolean canHarvest) {
    World world = player.getEntityWorld();
    BlockState state = world.getBlockState(pos);
    boolean removed =
        state.removedByPlayer(world, pos, player, canHarvest, world.getFluidState(pos));

    if (removed) {
      state.getBlock().onPlayerDestroy(world, pos, state);
    }
    return removed;
  }

  private static boolean matches(Block origin, Block target) {

    if (origin == target) {
      return true;
    } else {
      Set<ResourceLocation> originTags = origin.getTags();
      Set<ResourceLocation> targetTags = target.getTags();

      for (ResourceLocation originTag : originTags) {

        if (targetTags.contains(originTag)) {
          return true;
        }
      }
      return false;
    }
  }
}