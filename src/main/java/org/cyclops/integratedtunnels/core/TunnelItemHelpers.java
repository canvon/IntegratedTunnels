package org.cyclops.integratedtunnels.core;

import com.google.common.collect.Lists;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.cyclops.commoncapabilities.api.capability.itemhandler.ItemMatch;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.commoncapabilities.api.ingredient.storage.IIngredientComponentStorage;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueTypeListProxy;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetworkIngredients;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBlock;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeItemStack;
import org.cyclops.integratedtunnels.core.predicate.IngredientPredicate;
import org.cyclops.integratedtunnels.core.predicate.IngredientPredicateBlockList;
import org.cyclops.integratedtunnels.core.predicate.IngredientPredicateItemStackList;
import org.cyclops.integratedtunnels.core.predicate.IngredientPredicateItemStackNbt;
import org.cyclops.integratedtunnels.core.predicate.IngredientPredicateItemStackOperator;
import org.cyclops.integratedtunnels.part.aspect.ITunnelConnection;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author rubensworks
 */
public class TunnelItemHelpers {

    public static final IngredientPredicate<ItemStack, Integer> MATCH_NONE = new IngredientPredicate<ItemStack, Integer>(IngredientComponent.ITEMSTACK, ItemStack.EMPTY, ItemMatch.EXACT, false, true, 0, false) {
        @Override
        public boolean test(@Nullable ItemStack input) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == TunnelItemHelpers.MATCH_NONE;
        }

        @Override
        public int hashCode() {
            return 9991029;
        }
    };

    public static IngredientPredicate<ItemStack, Integer> matchAll(final int amount, final boolean exactAmount) {
        return new IngredientPredicate<ItemStack, Integer>(IngredientComponent.ITEMSTACK, new ItemStack(Items.APPLE, amount), exactAmount ? ItemMatch.STACKSIZE : ItemMatch.ANY, false, false, amount, exactAmount) {
            @Override
            public boolean test(ItemStack input) {
                return true;
            }
        };
    }

    public static IngredientPredicate<ItemStack, Integer> matchItemStack(final ItemStack itemStack, final boolean checkItem,
                                                                         final boolean checkStackSize, final boolean checkDamage,
                                                                         final boolean checkNbt, final boolean blacklist,
                                                                         final boolean exactAmount) {
        int matchFlags = ItemMatch.ANY;
        if (checkItem)      matchFlags = matchFlags | ItemMatch.ITEM;
        if (checkDamage)    matchFlags = matchFlags | ItemMatch.DAMAGE;
        if (checkNbt)       matchFlags = matchFlags | ItemMatch.NBT;
        if (checkStackSize) matchFlags = matchFlags | ItemMatch.STACKSIZE;
        return new IngredientPredicate<ItemStack, Integer>(IngredientComponent.ITEMSTACK, itemStack.copy(), matchFlags, blacklist, itemStack.isEmpty() && !blacklist,
                itemStack.getCount(), exactAmount) {
            @Override
            public boolean test(@Nullable ItemStack input) {
                boolean result = areItemStackEqual(input, itemStack, checkStackSize, true, checkDamage, checkNbt);
                if (blacklist) {
                    result = !result;
                }
                return result;
            }
        };
    }

    public static IngredientPredicate<ItemStack, Integer> matchItemStacks(final IValueTypeListProxy<ValueObjectTypeItemStack, ValueObjectTypeItemStack.ValueItemStack> itemStacks,
                                                                          final boolean checkItem, final boolean checkStackSize,
                                                                          final boolean checkDamage, final boolean checkNbt,
                                                                          final boolean blacklist, final int amount, final boolean exactAmount) {
        return new IngredientPredicateItemStackList(blacklist, amount, exactAmount, itemStacks, checkStackSize, checkItem, checkDamage, checkNbt);
    }

    public static IngredientPredicate<ItemStack, Integer> matchPredicateItem(final PartTarget partTarget, final IOperator predicate,
                                                                             final int amount, final boolean exactAmount) {
        return new IngredientPredicateItemStackOperator(amount, exactAmount, predicate, partTarget);
    }

    public static IngredientPredicate<ItemStack, Integer> matchBlocks(final IValueTypeListProxy<ValueObjectTypeBlock, ValueObjectTypeBlock.ValueBlock> blocks,
                                                                      final boolean checkItem, final boolean checkStackSize,
                                                                      final boolean checkDamage, final boolean checkNbt,
                                                                      final boolean blacklist, final int amount, final boolean exactAmount) {
        return new IngredientPredicateBlockList(blacklist, amount, exactAmount, blocks, checkStackSize, checkItem, checkDamage, checkNbt);
    }

    public static IngredientPredicate<ItemStack, Integer> matchPredicateBlock(final PartTarget partTarget, final IOperator predicate,
                                                                              final int amount, final boolean exactAmount) {
        return new IngredientPredicateBlockOperator(amount, exactAmount, predicate, partTarget);
    }

    public static IngredientPredicate<ItemStack, Integer> matchNbt(final NBTTagCompound tag, final boolean subset, final boolean superset,
                                                                   final boolean requireNbt, final boolean recursive, final boolean blacklist,
                                                                   final int amount, final boolean exactAmount) {
        return new IngredientPredicateItemStackNbt(blacklist, amount, exactAmount, requireNbt, subset, tag, recursive, superset);
    }

    public static boolean areItemStackEqual(ItemStack stackA, ItemStack stackB,
                                            boolean checkStackSize, boolean checkItem,
                                            boolean checkDamage, boolean checkNbt) {
        if (stackA == null && stackB == null) return true;
        if (stackA != null && stackB != null) {
            if (checkStackSize && stackA.getCount() != stackB.getCount()) return false;
            if (checkItem && stackA.getItem() != stackB.getItem()) return false;
            if (checkDamage && stackA.getItemDamage() != stackB.getItemDamage()) return false;
            if (checkNbt && !ItemStack.areItemStackTagsEqual(stackA, stackB)) return false;
            return true;
        }
        return false;
    }

    /**
     * Place item blocks from the given source in the world.
     * @param network The network in which the movement is happening.
     * @param ingredientsNetwork The ingredients network in which the movement is happening.
     * @param channel The channel.
     * @param connection The connection object.
     * @param source The source item storage.
     * @param world The destination world.
     * @param pos The destination position.
     * @param side The destination side.
     * @param itemStackMatcher The itemstack match predicate.
     * @param hand The hand to place the block with.
     * @param blockUpdate If a block update should occur after placement.
     * @param ignoreReplacable If replacable blocks should be overriden when placing blocks.
     * @param craftIfFailed If the exact ingredient from ingredientPredicate should be crafted if transfer failed.
     * @return The placed item.
     */
    public static ItemStack placeItems(INetwork network, IPositionedAddonsNetworkIngredients<ItemStack, Integer> ingredientsNetwork,
                                       int channel, ITunnelConnection connection,
                                       IIngredientComponentStorage<ItemStack, Integer> source,
                                       World world, BlockPos pos, EnumFacing side,
                                       IngredientPredicate<ItemStack, Integer> itemStackMatcher, EnumHand hand,
                                       boolean blockUpdate, boolean ignoreReplacable, boolean craftIfFailed) {
        IBlockState destBlockState = world.getBlockState(pos);
        final Material destMaterial = destBlockState.getMaterial();
        final boolean isDestNonSolid = !destMaterial.isSolid();
        final boolean isDestReplaceable = destBlockState.getBlock().isReplaceable(world, pos);
        if (!world.isAirBlock(pos)
                && (!isDestNonSolid || !(ignoreReplacable && isDestReplaceable))) {
            return null;
        }

        IIngredientComponentStorage<ItemStack, Integer> destinationBlock = new ItemStorageBlockWrapper(
                true, (WorldServer) world, pos, side, hand, blockUpdate, 0, false, ignoreReplacable, true);
        return TunnelHelpers.moveSingleStateOptimized(network, ingredientsNetwork, channel, connection, source, -1, destinationBlock, -1, itemStackMatcher, craftIfFailed);
    }

    /**
     * Pick up item blocks from the given source in the world.
     * @param network The network in which the movement is happening.
     * @param ingredientsNetwork The ingredients network in which the movement is happening.
     * @param channel The channel.
     * @param connection The connection object.
     * @param world The destination world.
     * @param pos The destination position.
     * @param side The destination side.
     * @param destination The destination item storage.
     * @param itemStackMatcher The itemstack match predicate.
     * @param hand The hand to place the block with.
     * @param blockUpdate If a block update should occur after placement.
     * @param ignoreReplacable If replacable blocks should be ignored from picking up.
     * @param fortune The fortune level.
     * @param silkTouch If the block should be broken with silk touch.
     * @param breakOnNoDrops If the block should be broken if it produced no drops.
     * @return The picked-up items.
     */
    public static List<ItemStack> pickUpItems(INetwork network, IPositionedAddonsNetworkIngredients<ItemStack, Integer> ingredientsNetwork,
                                              int channel, ITunnelConnection connection, World world, BlockPos pos, EnumFacing side,
                                              IIngredientComponentStorage<ItemStack, Integer> destination,
                                              IngredientPredicate<ItemStack, Integer> itemStackMatcher, EnumHand hand, boolean blockUpdate,
                                              boolean ignoreReplacable, int fortune, boolean silkTouch,
                                              boolean breakOnNoDrops) {
        IBlockState destBlockState = world.getBlockState(pos);
        final Material destMaterial = destBlockState.getMaterial();
        final boolean isDestReplaceable = destBlockState.getBlock().isReplaceable(world, pos);
        if (world.isAirBlock(pos)
                || ((ignoreReplacable && isDestReplaceable) || destMaterial.isLiquid())) {
            return null;
        }

        IIngredientComponentStorage<ItemStack, Integer> sourceBlock = new ItemStorageBlockWrapper(
                false, (WorldServer) world, pos, side, hand, blockUpdate, fortune, silkTouch, ignoreReplacable, breakOnNoDrops);
        List<ItemStack> itemStacks = Lists.newArrayList();
        ItemStack itemStack;
        while (!(itemStack = TunnelHelpers.moveSingleStateOptimized(network, ingredientsNetwork, channel, connection, sourceBlock, -1,
                destination, -1, itemStackMatcher, false)).isEmpty()) {
            itemStacks.add(itemStack);
        }
        return itemStacks;
    }

    /**
     * Helper function to get a copy of the given stack with the given stacksize.
     * @param prototype A prototype stack.
     * @param count A new stacksize.
     * @return A copy of the given stack with the given count.
     */
    public static ItemStack prototypeWithCount(ItemStack prototype, int count) {
        if (prototype.getCount() != count) {
            if (prototype.isEmpty()) {
                return new ItemStack(Items.APPLE, count);
            } else {
                prototype = prototype.copy();
                prototype.setCount(count);
            }
        }
        return prototype;
    }

}
