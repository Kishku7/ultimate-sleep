package com.kishku7.ultimatesleep.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Soft (runtime-only) integration with Travelers' Backpack: lets auto-sleep put a player to bed
 * "in place" using a sleeping bag when no real bed is reachable. We never compile against TB --
 * the mod is detected by id at runtime and its sleeping-bag block is referenced by registry id,
 * so Ultimate Sleep loads and runs identically whether or not TB is installed.
 *
 * Sleep-in-place mirrors how a bed works: place TB's sleeping-bag block (a BedBlock subclass) as
 * a foot+head pair at the player's feet, then call startSleepInBed on it. Ultimate Sleep owns the
 * cleanup (AutoSleepManager removes the block when the player wakes), so we do not rely on TB's own
 * config-gated removal.
 */
public final class TravelersBackpackCompat {

    private TravelersBackpackCompat() {}

    public static final String MOD_ID = "travelersbackpack";
    private static final Identifier SLEEPING_BAG_BLOCK =
            Identifier.fromNamespaceAndPath(MOD_ID, "red_sleeping_bag");

    public static boolean isPresent() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** True if the player is carrying a TB sleeping-bag item (hands or main inventory). */
    public static boolean hasSleepingBag(ServerPlayer p) {
        if (!isPresent()) return false;
        if (isSleepingBag(p.getMainHandItem()) || isSleepingBag(p.getOffhandItem())) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isSleepingBag(inv.getItem(i))) return true;
        }
        return false;
    }

    private static boolean isSleepingBag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith("sleeping_bag");
    }

    /**
     * Place a sleeping-bag bed at the player's feet and start sleeping in it.
     *
     * @return the two placed block positions {foot, head} for the caller to clean up on wake, or
     *         {@code null} if TB is absent, the block id is unknown, there's no room, or sleep was
     *         rejected (in which case nothing is left behind).
     */
    public static BlockPos[] placeAndSleep(ServerLevel level, ServerPlayer p) {
        if (!isPresent()) return null;
        Block bag = BuiltInRegistries.BLOCK.getOptional(SLEEPING_BAG_BLOCK).orElse(null);
        if (bag == null) return null;

        Direction facing = p.getDirection();
        BlockPos foot = p.blockPosition();
        BlockPos head = foot.relative(facing);

        // Need two clear cells with solid floor under the foot; otherwise don't disturb the world.
        if (!level.getBlockState(foot).canBeReplaced() || !level.getBlockState(head).canBeReplaced()) return null;
        if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)) return null;

        BlockState footState = bag.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT)
                .setValue(BlockStateProperties.OCCUPIED, false);
        BlockState headState = footState.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        level.setBlock(foot, footState, Block.UPDATE_ALL);
        level.setBlock(head, headState, Block.UPDATE_ALL);

        boolean[] ok = {true};
        p.startSleepInBed(foot).ifLeft(problem -> ok[0] = false);
        if (!ok[0]) {
            remove(level, foot, head);
            return null;
        }
        return new BlockPos[]{foot, head};
    }

    /** Remove a previously-placed sleeping bag (only if it's still our sleeping-bag block). */
    public static void remove(ServerLevel level, BlockPos foot, BlockPos head) {
        Block bag = BuiltInRegistries.BLOCK.getOptional(SLEEPING_BAG_BLOCK).orElse(null);
        for (BlockPos pos : new BlockPos[]{head, foot}) {
            if (pos != null && (bag == null || level.getBlockState(pos).is(bag))) {
                level.removeBlock(pos, false);
            }
        }
    }
}
