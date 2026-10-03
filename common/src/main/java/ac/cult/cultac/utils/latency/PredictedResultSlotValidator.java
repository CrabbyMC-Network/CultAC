package ac.cult.cultac.utils.latency;

import ac.cult.cultac.network.packet.InventoryPackets.MerchantOffer;
import ac.cult.cultac.utils.inventory.ItemUtil;
import ac.cult.cultac.utils.inventory.inventory.MenuType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PredictedResultSlotValidator {
    private PredictedResultSlotValidator() {}

    record ResultAllowance(Item material, long amount) {}

    static ResultAllowance dialogResultAllowance(
            MenuType menuType,
            int clickedSlot,
            List<ItemStack> beforeSlots,
            ItemStack beforeCarried,
            List<ItemStack> afterSlots,
            ItemStack afterCarried,
            List<MerchantOffer> merchantOffers,
            int selectedMerchantOffer) {
        int resultSlot = switch (menuType) {
            case STONECUTTER -> 1;
            case ANVIL, GRINDSTONE, MERCHANT, CARTOGRAPHY_TABLE -> 2;
            case LOOM -> 3;
            default -> -1;
        };
        if (resultSlot < 0 || resultSlot >= beforeSlots.size() || resultSlot >= afterSlots.size()) {
            return null;
        }

        ItemStack beforeResult = beforeSlots.get(resultSlot);
        ItemStack afterResult = afterSlots.get(resultSlot);
        if (menuType != MenuType.MERCHANT) {
            if (isEmpty(beforeResult)
                    || !isEmpty(afterResult) && !ItemUtil.isSameItemSameTags(beforeResult, afterResult)) {
                return null;
            }
            afterResult = beforeResult;
        }
        ItemStack result = !isEmpty(afterResult) ? afterResult : beforeResult;
        if (isEmpty(result) && clickedSlot == resultSlot) {
            if (menuType != MenuType.MERCHANT) {
                return null;
            }
            result = findClaimedResult(
                    menuType,
                    beforeSlots,
                    beforeCarried,
                    afterSlots,
                    afterCarried,
                    merchantOffers,
                    selectedMerchantOffer);
        }
        if (isEmpty(result)
                || !isSaneDialogResult(
                        menuType, beforeSlots, afterSlots, result, merchantOffers, selectedMerchantOffer)) {
            return null;
        }

        long credit = materialCount(afterSlots, afterCarried, result.getItem())
                - materialCount(beforeSlots, beforeCarried, result.getItem());
        if (credit <= 0) {
            return null;
        }
        if (clickedSlot == resultSlot && !inputsConsumed(menuType, beforeSlots, afterSlots)) {
            return null;
        }
        if (clickedSlot != resultSlot
                && amountOf(afterResult, result.getItem()) <= amountOf(beforeResult, result.getItem())) {
            return null;
        }

        return new ResultAllowance(result.getItem(), credit);
    }

    private static boolean isSaneDialogResult(
            MenuType menuType,
            List<ItemStack> beforeSlots,
            List<ItemStack> afterSlots,
            ItemStack result,
            List<MerchantOffer> merchantOffers,
            int selectedMerchantOffer) {
        return isSaneDialogResult(menuType, beforeSlots, result, merchantOffers, selectedMerchantOffer)
                || isSaneDialogResult(menuType, afterSlots, result, merchantOffers, selectedMerchantOffer);
    }

    static boolean isSaneDialogResult(
            MenuType menuType,
            List<ItemStack> slots,
            ItemStack result,
            List<MerchantOffer> merchantOffers,
            int selectedMerchantOffer) {
        if (slots == null || slots.isEmpty() || isEmpty(result)) {
            return false;
        }

        ItemStack input = slots.get(0);
        return switch (menuType) {
            case STONECUTTER ->
                !isEmpty(input)
                        && input.getItem() instanceof net.minecraft.world.item.BlockItem
                        && result.getItem() instanceof net.minecraft.world.item.BlockItem;
            case ANVIL -> sameMaterial(input, result) && result.getCount() <= input.getCount();
            case GRINDSTONE -> slots.size() >= 2 && saneGrindstoneResult(slots, result);
            case CARTOGRAPHY_TABLE -> slots.size() >= 2 && saneCartographyResult(slots, result);
            case LOOM ->
                slots.size() >= 2
                        && sameMaterial(input, result)
                        && ItemUtil.name(input.getItem()).endsWith("_BANNER")
                        && !isEmpty(slots.get(1))
                        && ItemUtil.name(slots.get(1).getItem()).endsWith("_DYE")
                        && result.getCount() == 1;
            case MERCHANT ->
                PredictedMerchantInventory.matchesResult(slots, merchantOffers, selectedMerchantOffer, result);
            default -> false;
        };
    }

    private static ItemStack findClaimedResult(
            MenuType menuType,
            List<ItemStack> beforeSlots,
            ItemStack beforeCarried,
            List<ItemStack> afterSlots,
            ItemStack afterCarried,
            List<MerchantOffer> merchantOffers,
            int selectedMerchantOffer) {
        List<ItemStack> candidates = new java.util.ArrayList<>(afterSlots.size() + 1);
        candidates.add(afterCarried);
        candidates.addAll(afterSlots);
        for (ItemStack candidate : candidates) {
            if (isEmpty(candidate)) {
                continue;
            }

            long gained = materialCount(afterSlots, afterCarried, candidate.getItem())
                    - materialCount(beforeSlots, beforeCarried, candidate.getItem());
            if (gained <= 0) {
                continue;
            }

            long consumedSameMaterial = consumedInputAmount(menuType, beforeSlots, afterSlots, candidate.getItem());
            ItemStack claimedResult = ItemUtil.copy(candidate);
            claimedResult.setCount((int) Math.min(Integer.MAX_VALUE, gained + consumedSameMaterial));
            if (isSaneDialogResult(
                    menuType, beforeSlots, afterSlots, claimedResult, merchantOffers, selectedMerchantOffer)) {
                return claimedResult;
            }
        }
        return ItemStack.EMPTY;
    }

    static boolean isClientClaimPlausible(
            List<ItemStack> beforeSlots,
            ItemStack beforeCarried,
            List<ItemStack> afterSlots,
            ItemStack afterCarried,
            boolean allowCreativeCreation) {
        return isClientClaimPlausible(
                beforeSlots, beforeCarried, afterSlots, afterCarried, allowCreativeCreation, null, 0);
    }

    static boolean isClientClaimPlausible(
            List<ItemStack> beforeSlots,
            ItemStack beforeCarried,
            List<ItemStack> afterSlots,
            ItemStack afterCarried,
            boolean allowCreativeCreation,
            Item creditedMaterial,
            long creditedAmount) {
        if (beforeSlots.size() != afterSlots.size()) {
            return false;
        }
        if (allowCreativeCreation) {
            return true;
        }

        Map<Item, Long> beforeCounts = new IdentityHashMap<>();
        Map<Item, Long> afterCounts = new IdentityHashMap<>();
        if (!addStacks(beforeCounts, beforeSlots) || !addStack(beforeCounts, beforeCarried)) {
            return false;
        }
        if (!addStacks(afterCounts, afterSlots) || !addStack(afterCounts, afterCarried)) {
            return false;
        }

        for (Map.Entry<Item, Long> entry : afterCounts.entrySet()) {
            long credit = entry.getKey() == creditedMaterial ? Math.max(0, creditedAmount) : 0;
            if (entry.getValue() > beforeCounts.getOrDefault(entry.getKey(), 0L) + credit) {
                return false;
            }
        }
        return true;
    }

    private static boolean addStacks(Map<Item, Long> counts, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!addStack(counts, stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean addStack(Map<Item, Long> counts, ItemStack stack) {
        if (isEmpty(stack)) {
            return true;
        }
        if (stack.getCount() <= 0 || stack.getItem() == null || stack.getItem() == Items.AIR) {
            return false;
        }

        counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
        return true;
    }

    static boolean costMatches(ItemStack cost, ItemStack stack) {
        return !isEmpty(cost) && !isEmpty(stack) && cost.getItem() == stack.getItem();
    }

    private static boolean saneGrindstoneResult(List<ItemStack> slots, ItemStack result) {
        int available = 0;
        for (int slot = 0; slot < 2; slot++) {
            ItemStack input = slots.get(slot);
            if (sameMaterial(input, result)
                    || !isEmpty(input) && input.getItem() == Items.ENCHANTED_BOOK && result.getItem() == Items.BOOK) {
                available += input.getCount();
            }
        }
        return available > 0 && result.getCount() <= available;
    }

    private static boolean saneCartographyResult(List<ItemStack> slots, ItemStack result) {
        ItemStack map = slots.get(0);
        ItemStack additional = slots.get(1);
        if (isEmpty(map)
                || isEmpty(additional)
                || map.getItem() != Items.FILLED_MAP
                || result.getItem() != Items.FILLED_MAP) {
            return false;
        }
        return additional.getItem() == Items.MAP
                ? result.getCount() == 2
                : (additional.getItem() == Items.PAPER || additional.getItem() == Items.GLASS_PANE)
                        && result.getCount() == 1;
    }

    private static boolean sameMaterial(ItemStack first, ItemStack second) {
        return !isEmpty(first) && !isEmpty(second) && first.getItem() == second.getItem();
    }

    private static boolean inputsConsumed(MenuType menuType, List<ItemStack> before, List<ItemStack> after) {
        int inputSlots = dialogInputSlots(menuType);
        long beforeAmount = 0;
        long afterAmount = 0;
        for (int slot = 0; slot < inputSlots && slot < before.size() && slot < after.size(); slot++) {
            beforeAmount += isEmpty(before.get(slot)) ? 0 : before.get(slot).getCount();
            afterAmount += isEmpty(after.get(slot)) ? 0 : after.get(slot).getCount();
        }
        return afterAmount < beforeAmount;
    }

    private static long consumedInputAmount(
            MenuType menuType, List<ItemStack> before, List<ItemStack> after, Item material) {
        int inputSlots = dialogInputSlots(menuType);
        long beforeAmount = 0;
        long afterAmount = 0;
        for (int slot = 0; slot < inputSlots && slot < before.size() && slot < after.size(); slot++) {
            beforeAmount += amountOf(before.get(slot), material);
            afterAmount += amountOf(after.get(slot), material);
        }
        return Math.max(0, beforeAmount - afterAmount);
    }

    private static int dialogInputSlots(MenuType menuType) {
        return switch (menuType) {
            case STONECUTTER -> 1;
            case ANVIL, GRINDSTONE, MERCHANT, CARTOGRAPHY_TABLE -> 2;
            case LOOM -> 3;
            default -> 0;
        };
    }

    private static long materialCount(List<ItemStack> slots, ItemStack carried, Item material) {
        long count = amountOf(carried, material);
        for (ItemStack slot : slots) {
            count += amountOf(slot, material);
        }
        return count;
    }

    private static int amountOf(ItemStack stack, Item material) {
        return material != null && !isEmpty(stack) && stack.getItem() == material ? stack.getCount() : 0;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}
