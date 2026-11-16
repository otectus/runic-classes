package com.otectus.runicclasses.integration.treasurebags;

import com.otectus.runicclasses.RunicClasses;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/**
 * Builds a Treasure Bags bag of a given type without compiling against the mod.
 *
 * <p>The stack is exactly what Treasure Bags' {@code set_bag_type} loot function produces:
 * {@code treasurebags:treasure_bag} carrying {@code silentlib.LootContainer.BagType = <id>} (verified
 * against {@code TreasureBagItem.setBagType} in treasure-bags 1.20.1-1.9.0). Before handing one out the
 * bag type is looked up through {@code BagTypeManager.getValue}, reflectively; a type that is not loaded
 * (Treasure Bags absent, its group disabled, a typo in a pack) returns an empty stack so the caller
 * gives the loot table's items directly instead of an unopenable bag.
 */
public final class TreasureBagsBridge {

    private static final String MOD_ID = "treasurebags";
    private static final ResourceLocation BAG_ITEM = new ResourceLocation(MOD_ID, "treasure_bag");
    private static Method getValue;
    private static boolean lookupFailed;

    private TreasureBagsBridge() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static ItemStack bagOf(ResourceLocation bagType) {
        if (!isLoaded() || !typeExists(bagType)) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(BAG_ITEM);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTagElement("silentlib.LootContainer").putString("BagType", bagType.toString());
        return stack;
    }

    private static boolean typeExists(ResourceLocation bagType) {
        if (lookupFailed) return false;
        try {
            if (getValue == null) {
                Class<?> manager = Class.forName("net.silentchaos512.treasurebags.lib.BagTypeManager");
                getValue = manager.getMethod("getValue", ResourceLocation.class);
            }
            return getValue.invoke(null, bagType) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            lookupFailed = true;
            RunicClasses.LOGGER.warn("Treasure Bags' bag type lookup is unavailable ({}); starter bags are given as items.", e.toString());
            return false;
        }
    }
}
