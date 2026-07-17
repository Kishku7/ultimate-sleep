package com.kishku7.ultimatesleep.compat;

import com.kishku7.ultimatesleep.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//if compat.modern_fx(ver): cog.outl("import net.minecraft.core.component.TypedDataComponent;")
//]]]
import net.minecraft.core.component.TypedDataComponent;
//[[[end]]]
import net.minecraft.core.registries.BuiltInRegistries;
//[[[cog
//cog.outl("import %s;" % compat.id_import(ver))
//]]]
import net.minecraft.resources.Identifier;
//[[[end]]]
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Soft (runtime-only) integration with Travelers' Backpack: lets auto-sleep put a player to bed
 * "in place" using a sleeping bag when no real bed is reachable. We never compile against TB --
 * the mod is detected by id at runtime, its sleeping-bag block is referenced by registry id, and
 * the one TB helper we need (getWearingBackpack) is called reflectively. So Ultimate Sleep loads
 * and runs identically whether or not TB is installed.
 *
 * Attached-bag detection: 1.20.5+ reads the travelersbackpack "sleeping_bag_color" DATA COMPONENT;
 * older versions best-effort scan the stack NBT for a sleeping-bag key (TB's pre-component storage)
 * -- if TB's key drifts on an old line the worst case is the attached-bag path simply not
 * triggering (the loose-bag item path is version-neutral).
 */
public final class TravelersBackpackCompat {

    private TravelersBackpackCompat() {}

    public static final String MOD_ID = "travelersbackpack";
    //[[[cog
    //cog.outl("    private static final %s SLEEPING_BAG_BLOCK =" % compat.id_cls(ver))
    //cog.outl("            %s;" % compat.make_id(ver, "MOD_ID", '"red_sleeping_bag"'))
    //]]]
    private static final Identifier SLEEPING_BAG_BLOCK =
            Identifier.fromNamespaceAndPath(MOD_ID, "red_sleeping_bag");
    //[[[end]]]

    public static boolean isPresent() {
        return Platform.isModLoaded(MOD_ID);
    }

    /** True if the player can use a sleeping bag: a loose bag item, or one attached to their backpack. */
    public static boolean hasSleepingBag(ServerPlayer p) {
        if (!isPresent()) return false;
        // 1. Loose sleeping-bag item in hands / main inventory.
        if (isSleepingBagItem(p.getMainHandItem()) || isSleepingBagItem(p.getOffhandItem())) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isSleepingBagItem(s) || hasAttachedBag(s)) return true;
        }
        // 2. Bag attached to the worn backpack (TB resolves the Trinkets slot for us).
        if (hasAttachedBag(p.getMainHandItem()) || hasAttachedBag(p.getOffhandItem())) return true;
        return hasAttachedBag(wornBackpack(p));
    }

    private static boolean isSleepingBagItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        //[[[cog
        //cog.outl("        %s id = BuiltInRegistries.ITEM.getKey(stack.getItem());" % compat.id_cls(ver))
        //]]]
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        //[[[end]]]
        return id != null && MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith("sleeping_bag");
    }

    /** A TB backpack stack with a sleeping bag attached. */
    private static boolean hasAttachedBag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        //[[[cog
        //if compat.modern_fx(ver):
        //    cog.outl('        for (TypedDataComponent<?> comp : stack.getComponents()) {')
        //    cog.outl('            %s id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(comp.type());' % compat.id_cls(ver))
        //    cog.outl('            if (id != null && MOD_ID.equals(id.getNamespace()) && id.getPath().contains("sleeping_bag")) {')
        //    cog.outl('                Object v = comp.value();')
        //    cog.outl('                return !(v instanceof Integer i) || i >= 0;')
        //    cog.outl('            }')
        //    cog.outl('        }')
        //    cog.outl('        return false;')
        //else:
        //    cog.outl('        var tag = stack.getTag();')
        //    cog.outl('        if (tag == null) return false;')
        //    cog.outl('        for (String key : tag.getAllKeys()) {')
        //    cog.outl('            String k = key.toLowerCase();')
        //    cog.outl('            if (k.contains("sleeping") && k.contains("bag")) return true;')
        //    cog.outl('        }')
        //    cog.outl('        return false;')
        //]]]
        for (TypedDataComponent<?> comp : stack.getComponents()) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(comp.type());
            if (id != null && MOD_ID.equals(id.getNamespace()) && id.getPath().contains("sleeping_bag")) {
                Object v = comp.value();
                return !(v instanceof Integer i) || i >= 0;
            }
        }
        return false;
        //[[[end]]]
    }

    /** The TB backpack the player is wearing (handles the Trinkets slot internally), or EMPTY. */
    private static ItemStack wornBackpack(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils");
            Object r = c.getMethod("getWearingBackpack", Player.class).invoke(null, p);
            return (r instanceof ItemStack s) ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
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
        //[[[cog
        //import sys; sys.path.insert(0, codegen); import compat
        //for _l in compat.start_sleep_pre(ver, "        ", "level", "foot"): cog.outl(_l)
        //cog.outl("        " + compat.start_sleep_call(ver, "p", "level", "foot") + ".ifLeft(problem -> ok[0] = false);")
        //]]]
        p.startSleepInBed(foot).ifLeft(problem -> ok[0] = false);
        //[[[end]]]
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
